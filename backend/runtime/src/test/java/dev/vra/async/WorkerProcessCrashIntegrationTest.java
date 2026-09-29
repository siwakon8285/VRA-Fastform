package dev.vra.async;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import dev.vra.async.process.WorkerProcessHarness;
import dev.vra.async.simulator.SimulatorProcessHarness;
import dev.vra.async.simulator.SimulatorStore;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

/** G4 uses real child PIDs, PostgreSQL locks, and an independent simulator JVM/DB. */
@Tag("postgres")
class WorkerProcessCrashIntegrationTest {
    private static final String PASSWORD = "stage-i-disposable-test-only";
    private static final String ASYNC_PASSWORD = "async-disposable-test-only";
    private static final Duration DEADLINE = Duration.ofSeconds(18);
    private static final PostgreSQLContainer VRA = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static final PostgreSQLContainer SIMULATOR = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("simulator_stage_i").withUsername("simulator_test").withPassword(PASSWORD);
    private static SimulatorProcessHarness simulatorProcess;
    private static SimulatorStore simulatorStore;

    private record Fixture(UUID eventId, Payload payload) {}
    @FunctionalInterface private interface Condition { boolean satisfied() throws Exception; }

    @BeforeAll static void start() throws Exception {
        try {
            VRA.start();
            SIMULATOR.start();
            bootstrap();
            AsyncRoleBootstrap.run(VRA);
            assertEquals(3, new MigrationRunner().migrate(VRA.getJdbcUrl(), "vra_migrator", PASSWORD));
            simulatorProcess = new SimulatorProcessHarness(SIMULATOR);
            simulatorStore = new SimulatorStore(SIMULATOR.getJdbcUrl(), SIMULATOR.getUsername(),
                    SIMULATOR.getPassword());
            assertNotEquals(VRA.getJdbcUrl(), SIMULATOR.getJdbcUrl());
            assertNotEquals(ProcessHandle.current().pid(), simulatorProcess.pid());
        } catch (Exception failure) {
            stop();
            throw failure;
        }
    }

    @AfterAll static void stop() {
        if (simulatorProcess != null) simulatorProcess.close();
        SIMULATOR.stop();
        VRA.stop();
    }

    @BeforeEach void clear() throws Exception {
        owner("TRUNCATE vra.consumer_inbox,vra.reservation_projection,vra.outbox_event CASCADE");
    }

    @Test void committedScenarioAClaimSurvivesAbruptPidDeathAndFreshPidReclaims() throws Exception {
        Fixture fixture = fixture(TargetCode.RESERVATION_PROJECTION);
        try (Connection inboxBarrier = lock(VRA, "LOCK TABLE vra.consumer_inbox IN SHARE MODE")) {
            try (WorkerProcessHarness oldWorker = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                await("committed delivery claim", () -> "PROCESSING".equals(state(fixture)), oldWorker);
                UUID oldToken = token(fixture);
                assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                        + fixture.eventId() + "' AND action_code='CLAIM'"));
                assertNotNull(oldToken);
                assertNotEquals(0, oldWorker.killForcibly());
                assertFalse(oldWorker.alive());
                inboxBarrier.commit();
                assertEquals(0, inboxCount(fixture));
                await("PostgreSQL lease expiry", () -> expired(fixture), null);
                assertEquals(oldToken, token(fixture)); // expiry did not transfer ownership
                try (WorkerProcessHarness fresh = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                    assertNotEquals(oldWorker.pid(), fresh.pid());
                    await("fresh worker success", () -> "SUCCEEDED".equals(state(fixture)), fresh);
                    UUID newToken = historyToken(fixture, "RECLAIM");
                    assertNotEquals(oldToken, newToken);
                    assertEquals(2, count("SELECT delivery_attempt_count FROM vra.outbox_delivery WHERE event_id='"
                            + fixture.eventId() + "'"));
                    assertEquals(1, inboxCount(fixture));
                    assertEquals(1, projectionCount(fixture));
                    assertFalse(completeWithOldToken(fixture, oldToken));
                    System.out.println("G4 A claim PID " + oldWorker.pid() + " -> " + fresh.pid()
                            + ", tokens " + oldToken + " -> " + newToken);
                }
            }
        }
    }

    @Test void committedConsumerEffectSurvivesKillBeforeDeliveryFinalize() throws Exception {
        Fixture fixture = fixture(TargetCode.RESERVATION_PROJECTION);
        try (Connection inboxBarrier = lock(VRA, "LOCK TABLE vra.consumer_inbox IN SHARE MODE")) {
            try (WorkerProcessHarness oldWorker = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                await("claim before consumer", () -> "PROCESSING".equals(state(fixture)), oldWorker);
                UUID oldToken = token(fixture);
                try (Connection finalizeBarrier = lock(VRA, "SELECT 1 FROM vra.outbox_delivery WHERE event_id='"
                        + fixture.eventId() + "' FOR UPDATE")) {
                    inboxBarrier.commit();
                    await("committed inbox/projection before finalize", () -> inboxCount(fixture) == 1
                            && projectionCount(fixture) == 1, oldWorker);
                    assertEquals("PROCESSING", state(fixture));
                    assertNotEquals(0, oldWorker.killForcibly());
                    finalizeBarrier.commit();
                }
                try (WorkerProcessHarness fresh = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                    assertNotEquals(oldWorker.pid(), fresh.pid());
                    await("inbox dedupe recovery", () -> "SUCCEEDED".equals(state(fixture)), fresh);
                    assertNotEquals(oldToken, historyToken(fixture, "RECLAIM"));
                    assertFalse(completeWithOldToken(fixture, oldToken));
                    assertEquals(1, inboxCount(fixture));
                    assertEquals(1, projectionCount(fixture));
                    System.out.println("G4 A post-effect PID " + oldWorker.pid() + " -> " + fresh.pid());
                }
            }
        }
    }

    @Test void killedExternalRequestReclaimsToUnknownBeforeAnyFurtherExecute() throws Exception {
        Fixture fixture = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        control("block-next");
        try (WorkerProcessHarness oldWorker = worker(TargetCode.VALIDATION_EXTERNAL_EFFECT, 1, 1)) {
            try {
                await("simulator PENDING", () -> {
                    SimulatorStore.Snapshot row = simulatorStore.read(fixture.eventId());
                    return row != null && "PENDING".equals(row.state());
                }, oldWorker);
                assertEquals("PROCESSING", state(fixture));
                UUID oldToken = token(fixture);
                assertNotEquals(0, oldWorker.killForcibly());
                control("release");
                await("independent simulator effect", () -> simulatorStore.read(fixture.eventId()).effectCount() == 1,
                        null);
                assertTrue(relinquishWithWorker(fixture.eventId(), oldToken));
                assertEquals("PROCESSING", state(fixture));
                assertEquals("true", value("SELECT claim_relinquished::text FROM vra.outbox_delivery "
                        + "WHERE event_id='" + fixture.eventId() + "'"));
                assertFalse(completeWithOldToken(fixture, oldToken));
                assertNull(handoffWithOldToken(fixture, oldToken));
                try (WorkerProcessHarness fresh = worker(TargetCode.VALIDATION_EXTERNAL_EFFECT, 1, 1)) {
                    assertNotEquals(oldWorker.pid(), fresh.pid());
                    await("reclaimed UNKNOWN handoff", () -> "RECONCILIATION_REQUIRED".equals(state(fixture)), fresh);
                    assertEquals("RECLAIM_POSSIBLE_EXTERNAL_SEND", value("SELECT reason_code FROM vra.outbox_delivery "
                            + "WHERE event_id='" + fixture.eventId() + "'"));
                    assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='"
                            + fixture.eventId() + "'"));
                    assertFalse(completeWithOldToken(fixture, oldToken));
                    try (WorkerProcessHarness reconciler = reconciler()) {
                        await("fresh reconciler confirms simulator success",
                                () -> "SUCCEEDED".equals(state(fixture)), reconciler);
                        assertNotEquals(fresh.pid(), reconciler.pid());
                        assertEquals(1, simulatorStore.read(fixture.eventId()).effectCount());
                        assertEquals(1, simulatorOperationCount(fixture));
                        assertQueryBeforeSecondExecute(fixture);
                        System.out.println("G4 B blocked PID " + oldWorker.pid() + " -> " + fresh.pid()
                                + ", reconciler " + reconciler.pid());
                    }
                }
            } finally {
                // The simulator gate remains independent of worker death.
                control("clear");
            }
        }
    }

    @Test void lostResponseAfterDurableEffectIsRecoveredByFreshWorkerAndReconcilerPids() throws Exception {
        Fixture fixture = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        control("drop-next");
        try (Connection registrationBarrier = lock(SIMULATOR,
                "LOCK TABLE public.sim_operation IN SHARE MODE")) {
            try (WorkerProcessHarness oldWorker = worker(TargetCode.VALIDATION_EXTERNAL_EFFECT, 1, 1)) {
                await("worker claim before simulator registration", () -> "PROCESSING".equals(state(fixture)), oldWorker);
                UUID oldToken = token(fixture);
                try (Connection finalizeBarrier = lock(VRA, "SELECT 1 FROM vra.outbox_delivery WHERE event_id='"
                        + fixture.eventId() + "' FOR UPDATE")) {
                    registrationBarrier.commit();
                    await("effect committed before blocked UNKNOWN handoff", () -> {
                        SimulatorStore.Snapshot row = simulatorStore.read(fixture.eventId());
                        return row != null && row.effectCount() == 1;
                    }, oldWorker);
                    assertEquals("PROCESSING", state(fixture));
                    assertNotEquals(0, oldWorker.killForcibly());
                    finalizeBarrier.commit();
                }
                try (WorkerProcessHarness fresh = worker(TargetCode.VALIDATION_EXTERNAL_EFFECT, 1, 1)) {
                    assertNotEquals(oldWorker.pid(), fresh.pid());
                    await("UNKNOWN after lost response", () -> "RECONCILIATION_REQUIRED".equals(state(fixture)), fresh);
                    assertEquals("UNKNOWN_OUTCOME", value("SELECT failure_class FROM vra.outbox_delivery WHERE event_id='"
                            + fixture.eventId() + "'"));
                    assertFalse(completeWithOldToken(fixture, oldToken));
                    try (WorkerProcessHarness reconciler = reconciler()) {
                        await("confirmed external success", () -> "SUCCEEDED".equals(state(fixture)), reconciler);
                        assertEquals("CONFIRMED_SUCCEEDED", value("SELECT external_knowledge FROM "
                                + "vra.reconciliation_case WHERE event_id='" + fixture.eventId() + "'"));
                        assertEquals(1, simulatorStore.read(fixture.eventId()).effectCount());
                        assertEquals(1, simulatorOperationCount(fixture));
                        assertQueryBeforeSecondExecute(fixture);
                        System.out.println("G4 B lost-response PID " + oldWorker.pid() + " -> " + fresh.pid()
                                + ", reconciler " + reconciler.pid());
                    }
                }
            }
        }
    }

    @Test void boundedClaimsAndUnavailableRelinquishmentFallBackToLeaseExpiry() throws Exception {
        Fixture first = fixture(TargetCode.RESERVATION_PROJECTION);
        Fixture second = fixture(TargetCode.RESERVATION_PROJECTION);
        Fixture third = fixture(TargetCode.RESERVATION_PROJECTION);
        try (Connection inboxBarrier = lock(VRA, "LOCK TABLE vra.consumer_inbox IN SHARE MODE")) {
            try (WorkerProcessHarness worker = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                await("one bounded claim", () -> processingCount() == 1, worker);
                assertEquals(2, readyCount());
                assertEquals(0, count("SELECT count(*) FROM vra.consumer_inbox"));
                UUID claimed = UUID.fromString(value("SELECT event_id::text FROM vra.outbox_delivery "
                        + "WHERE state='PROCESSING'"));
                UUID oldToken = UUID.fromString(value("SELECT claim_token::text FROM vra.outbox_delivery "
                        + "WHERE event_id='" + claimed + "'"));
                try (Connection rowBarrier = lock(VRA, "SELECT 1 FROM vra.outbox_delivery WHERE event_id='"
                        + claimed + "' FOR UPDATE")) {
                    assertNotEquals(0, worker.killForcibly());
                    assertRelinquishTimesOut(claimed, oldToken);
                    rowBarrier.commit();
                }
                assertEquals("PROCESSING", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                        + claimed + "'"));
                assertEquals(oldToken.toString(), value("SELECT claim_token::text FROM vra.outbox_delivery "
                        + "WHERE event_id='" + claimed + "'"));
                inboxBarrier.commit();
                await("lease expiry without ownership transfer", () -> expired(claimed), null);
                assertEquals(oldToken.toString(), value("SELECT claim_token::text FROM vra.outbox_delivery "
                        + "WHERE event_id='" + claimed + "'"));
                try (WorkerProcessHarness fresh = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                    await("all durable ready work converges", () -> "SUCCEEDED".equals(state(first))
                            && "SUCCEEDED".equals(state(second)) && "SUCCEEDED".equals(state(third)), fresh);
                    assertNotEquals(oldToken, historyToken(claimed, "RECLAIM"));
                    assertEquals(3, count("SELECT count(*) FROM vra.consumer_inbox"));
                }
            }
        }
    }

    @Test void stoppedAttemptRelinquishesWithoutChangingProcessingOrTransferringOwnership() throws Exception {
        Fixture fixture = fixture(TargetCode.RESERVATION_PROJECTION);
        try (Connection inboxBarrier = lock(VRA, "LOCK TABLE vra.consumer_inbox IN SHARE MODE")) {
            try (WorkerProcessHarness oldWorker = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                await("claim before safe relinquishment", () -> "PROCESSING".equals(state(fixture)), oldWorker);
                UUID oldToken = token(fixture);
                assertNotEquals(0, oldWorker.killForcibly()); // attempt has stopped; no effect committed
                assertEquals(0, inboxCount(fixture));
                assertTrue(relinquishWithWorker(fixture.eventId(), oldToken));
                assertEquals("PROCESSING", state(fixture));
                assertEquals(oldToken, token(fixture));
                assertEquals("true", value("SELECT claim_relinquished::text FROM vra.outbox_delivery "
                        + "WHERE event_id='" + fixture.eventId() + "'"));
                assertFalse(completeWithOldToken(fixture, oldToken));
                assertNull(retryWithOldToken(fixture, oldToken));
                assertFalse(failWithOldToken(fixture, oldToken));
                assertFalse(relinquishWithWorker(fixture.eventId(), oldToken));
                assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                        + fixture.eventId() + "' AND action_code='RELINQUISH'"));
                try (WorkerProcessHarness fresh = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                    await("fresh token after relinquishment", () -> {
                        String current = value("SELECT claim_token::text FROM vra.outbox_delivery WHERE event_id='"
                                + fixture.eventId() + "'");
                        return !oldToken.toString().equals(current);
                    }, fresh);
                    assertNotEquals(oldToken, historyToken(fixture, "RECLAIM"));
                    assertEquals(2, count("SELECT delivery_attempt_count FROM vra.outbox_delivery "
                            + "WHERE event_id='" + fixture.eventId() + "'"));
                    assertEquals("PROCESSING", state(fixture));
                    inboxBarrier.commit();
                    await("successful safe redelivery", () -> "SUCCEEDED".equals(state(fixture)), fresh);
                    assertEquals(1, inboxCount(fixture));
                    assertEquals(1, projectionCount(fixture));
                }
            }
        }
    }

    @Test void productionDrainRelinquishesStoppedAttemptWithCurrentToken() throws Exception {
        Fixture fixture = fixture(TargetCode.RESERVATION_PROJECTION);
        try (Connection inboxBarrier = lock(VRA, "LOCK TABLE vra.consumer_inbox IN SHARE MODE")) {
            try (WorkerProcessHarness draining = worker(TargetCode.RESERVATION_PROJECTION, 1, 1,
                    "2s", "10s")) {
                await("committed claim before consumer barrier", () -> "PROCESSING".equals(state(fixture)), draining);
                UUID oldToken = token(fixture);
                try (Connection finalizeBarrier = lock(VRA, "SELECT 1 FROM vra.outbox_delivery WHERE event_id='"
                        + fixture.eventId() + "' FOR UPDATE")) {
                    inboxBarrier.commit();
                    await("consumer committed before blocked finalization", () -> inboxCount(fixture) == 1,
                            draining);
                    draining.requestGracefulShutdown();
                    await("worker drain admission closed", () -> draining.drainAdmissionClosed("outbox-drain"),
                            draining);
                    await("stopped attempt reached guarded relinquishment", () -> activeWorkerSql(
                            "async_relinquish_delivery"), draining);
                    finalizeBarrier.commit();
                }
                await("production guarded relinquishment", () -> count("SELECT count(*) FROM "
                        + "vra.outbox_delivery_history WHERE event_id='" + fixture.eventId()
                        + "' AND action_code='RELINQUISH'") == 1, draining);
                assertEquals("PROCESSING", state(fixture));
                assertEquals("true", value("SELECT claim_relinquished::text FROM vra.outbox_delivery "
                        + "WHERE event_id='" + fixture.eventId() + "'"));
                assertEquals(oldToken, token(fixture));
                assertFalse(completeWithOldToken(fixture, oldToken));
                assertNull(retryWithOldToken(fixture, oldToken));
                assertFalse(failWithOldToken(fixture, oldToken));
                draining.awaitExit(Duration.ofSeconds(12));
                try (WorkerProcessHarness fresh = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                    assertNotEquals(draining.pid(), fresh.pid());
                    await("normal reclaim after drain relinquishment",
                            () -> "SUCCEEDED".equals(state(fixture)), fresh);
                    assertNotEquals(oldToken, historyToken(fixture, "RECLAIM"));
                    assertEquals(2, count("SELECT delivery_attempt_count FROM vra.outbox_delivery WHERE event_id='"
                            + fixture.eventId() + "'"));
                    assertEquals(1, inboxCount(fixture));
                }
            }
        }
    }

    @Test void gracefulDrainLeavesUnclaimedWorkDurableAndStartsNoNewEffect() throws Exception {
        Fixture first = fixture(TargetCode.RESERVATION_PROJECTION);
        Fixture second = fixture(TargetCode.RESERVATION_PROJECTION);
        Fixture third = fixture(TargetCode.RESERVATION_PROJECTION);
        try (Connection inboxBarrier = lock(VRA, "LOCK TABLE vra.consumer_inbox IN SHARE MODE")) {
            try (WorkerProcessHarness draining = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                await("bounded in-flight work before drain", () -> processingCount() == 1, draining);
                assertEquals(2, readyCount());
                int claimsAtDrain = count("SELECT count(*) FROM vra.outbox_delivery_history "
                        + "WHERE action_code IN ('CLAIM','RECLAIM')");
                draining.requestGracefulShutdown();
                draining.awaitExit(Duration.ofSeconds(8));
                assertEquals(claimsAtDrain, count("SELECT count(*) FROM vra.outbox_delivery_history "
                        + "WHERE action_code IN ('CLAIM','RECLAIM')"));
                assertEquals(2, readyCount());
                assertEquals(0, processingCount());
                assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery WHERE state='RETRY_WAIT' "
                        + "AND failure_class='RETRYABLE_TRANSIENT' AND reason_code='RETRYABLE_TRANSIENT'"));
                assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history "
                        + "WHERE action_code='RETRY_SCHEDULED'"));
                assertEquals(0, count("SELECT count(*) FROM vra.consumer_inbox"));
                inboxBarrier.commit();
                try (WorkerProcessHarness fresh = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                    await("durable backlog after drain", () -> "SUCCEEDED".equals(state(first))
                            && "SUCCEEDED".equals(state(second)) && "SUCCEEDED".equals(state(third)), fresh);
                    assertEquals(3, count("SELECT count(*) FROM vra.consumer_inbox"));
                }
            }
        }
    }

    @Test void gracefulDrainAllowsCommittedConsumerToGuardedFinalize() throws Exception {
        Fixture inFlight = fixture(TargetCode.RESERVATION_PROJECTION);
        Fixture stillReady = fixture(TargetCode.RESERVATION_PROJECTION);
        try (Connection inboxBarrier = lock(VRA, "LOCK TABLE vra.consumer_inbox IN SHARE MODE")) {
            try (WorkerProcessHarness draining = worker(TargetCode.RESERVATION_PROJECTION, 1, 1)) {
                await("claim before safe drain", () -> processingCount() == 1, draining);
                UUID claimed = UUID.fromString(value("SELECT event_id::text FROM vra.outbox_delivery "
                        + "WHERE state='PROCESSING'"));
                try (Connection finalizeBarrier = lock(VRA, "SELECT 1 FROM vra.outbox_delivery WHERE event_id='"
                        + claimed + "' FOR UPDATE")) {
                    inboxBarrier.commit();
                    await("consumer committed while finalize waits", () -> count(
                            "SELECT count(*) FROM vra.consumer_inbox WHERE event_id='" + claimed + "'") == 1,
                            draining);
                    int claimsBefore = count("SELECT count(*) FROM vra.outbox_delivery_history "
                            + "WHERE action_code IN ('CLAIM','RECLAIM')");
                    draining.requestGracefulShutdown();
                    await("drain acceptance closed before finalize barrier releases",
                            () -> draining.drainAdmissionClosed("outbox-drain"), draining);
                    finalizeBarrier.commit();
                    draining.awaitExit(Duration.ofSeconds(8));
                    assertEquals("SUCCEEDED", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                            + claimed + "'"));
                    assertEquals("READY", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                            + (claimed.equals(inFlight.eventId()) ? stillReady.eventId() : inFlight.eventId()) + "'"));
                    assertEquals(claimsBefore, count("SELECT count(*) FROM vra.outbox_delivery_history "
                            + "WHERE action_code IN ('CLAIM','RECLAIM')"));
                    assertEquals(1, count("SELECT count(*) FROM vra.consumer_inbox"));
                }
            }
        }
    }

    @Test void externalDrainDoesNotClaimOrExecuteTheNextDurableEvent() throws Exception {
        Fixture inFlight = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        Fixture stillReady = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        control("block-next");
        try {
            try (WorkerProcessHarness draining = worker(TargetCode.VALIDATION_EXTERNAL_EFFECT, 1, 1)) {
                await("first execute accepted by independent simulator", () -> {
                    SimulatorStore.Snapshot operation = simulatorStore.read(inFlight.eventId());
                    return operation != null && "PENDING".equals(operation.state())
                            && simulatorRequestCount(inFlight, "EXECUTE") == 1;
                }, draining);
                assertEquals("PROCESSING", state(inFlight));
                assertEquals("READY", state(stillReady));
                assertEquals(1, processingCount());
                assertEquals(0, simulatorRequestCount(stillReady, "EXECUTE"));
                int claimsBefore = claimHistoryCount();
                draining.requestGracefulShutdown();
                await("external worker admission closed", () -> draining.drainAdmissionClosed("outbox-drain"),
                        draining);
                assertEquals(claimsBefore, claimHistoryCount());
                assertEquals("READY", state(stillReady));
                control("release");
                draining.awaitExit(Duration.ofSeconds(8));
                assertEquals(claimsBefore, claimHistoryCount());
                assertEquals("READY", state(stillReady));
                assertEquals(0, simulatorRequestCount(stillReady, "EXECUTE"));
                assertEquals(1, simulatorRequestCount(inFlight, "EXECUTE"));
                assertEquals(1, simulatorStore.read(inFlight.eventId()).effectCount());
                assertEquals("SUCCEEDED", state(inFlight));
            }
        } finally {
            control("clear");
        }
    }

    @Test void wrongWorkerAndReconcilerDatabaseIdentitiesFailStartup() throws Exception {
        Map<String, String> wrongWorker = common("OUTBOX_WORKER", "vra_runtime", 1, 1);
        wrongWorker.put("VRA_ASYNC_DB_PASSWORD", PASSWORD);
        try (WorkerProcessHarness process = new WorkerProcessHarness(wrongWorker)) {
            assertNotEquals(0, process.awaitExit(Duration.ofSeconds(5)));
            assertTrue(process.diagnostics().contains("outbox worker identity"), process.diagnostics());
        }
        Map<String, String> wrongReconciler = common("RECONCILER", "vra_runtime", 1, 1);
        wrongReconciler.put("VRA_ASYNC_DB_PASSWORD", PASSWORD);
        wrongReconciler.put("VRA_ASYNC_VALIDATION_MODE", "true");
        wrongReconciler.put("VRA_SIMULATOR_URL", simulatorProcess.baseUri().toString());
        try (WorkerProcessHarness process = new WorkerProcessHarness(wrongReconciler)) {
            assertNotEquals(0, process.awaitExit(Duration.ofSeconds(5)));
            assertTrue(process.diagnostics().contains("reconciliation worker identity"), process.diagnostics());
        }
        Map<String, String> noValidationMode = common("RECONCILER", "vra_reconciliation_worker", 1, 1);
        noValidationMode.put("VRA_SIMULATOR_URL", simulatorProcess.baseUri().toString());
        try (WorkerProcessHarness process = new WorkerProcessHarness(noValidationMode)) {
            assertNotEquals(0, process.awaitExit(Duration.ofSeconds(5)));
            assertTrue(process.diagnostics().contains("explicit validation mode"), process.diagnostics());
        }
    }

    @Test void reconcilerDrainStopsClaimsAndFreshPidRediscoversDurableCase() throws Exception {
        Fixture fixture = unknownCaseFixture();
        Fixture second = unknownCaseFixture();
        try (Connection observationBarrier = lock(SIMULATOR,
                "LOCK TABLE public.sim_operation IN ACCESS EXCLUSIVE MODE")) {
            try (WorkerProcessHarness oldReconciler = reconciler()) {
                await("one committed CHECKING before blocked observation", () -> count(
                        "SELECT count(*) FROM vra.reconciliation_case WHERE state='CHECKING'") == 1,
                        oldReconciler);
                assertEquals(0, count("SELECT lifetime_attempt_count FROM vra.reconciliation_case "
                        + "WHERE event_id='" + second.eventId() + "'"));
                oldReconciler.requestGracefulShutdown();
                await("reconciler admission closed", () -> oldReconciler.drainAdmissionClosed(
                        "reconciliation-drain"), oldReconciler);
                observationBarrier.commit();
                oldReconciler.awaitExit(Duration.ofSeconds(8));
                assertEquals(0, count("SELECT lifetime_attempt_count FROM vra.reconciliation_case "
                        + "WHERE event_id='" + second.eventId() + "'"));
                assertEquals("PENDING", value("SELECT state FROM vra.reconciliation_case WHERE event_id='"
                        + second.eventId() + "'"));
                try (WorkerProcessHarness fresh = reconciler()) {
                    assertNotEquals(oldReconciler.pid(), fresh.pid());
                    await("fresh reconciler observes both durable cases", () -> "RETRY_WAIT".equals(state(fixture))
                            && "RETRY_WAIT".equals(state(second)), fresh);
                    assertEquals(0, simulatorOperationCount(fixture));
                    assertEquals(0, simulatorOperationCount(second));
                }
            }
        }
    }

    private static Fixture unknownCaseFixture() throws Exception {
        Fixture fixture = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        UUID claimedToken;
        try (Connection worker = connection("vra_outbox_worker"); Statement claim = worker.createStatement();
             ResultSet row = claim.executeQuery("SELECT claim_token FROM vra.async_claim_delivery("
                     + "'VALIDATION_EXTERNAL_EFFECT'::varchar,'stage-i-setup'::varchar,1,4000)")) {
            assertTrue(row.next());
            claimedToken = row.getObject(1, UUID.class);
        }
        try (Connection worker = connection("vra_outbox_worker"); PreparedStatement handoff = worker.prepareStatement(
                "SELECT vra.async_handoff_unknown(?::uuid,?::uuid,'UNKNOWN_EXTERNAL_RESULT'::varchar)")) {
            handoff.setObject(1, fixture.eventId());
            handoff.setObject(2, claimedToken);
            try (ResultSet row = handoff.executeQuery()) {
                assertTrue(row.next());
                assertNotNull(row.getObject(1, UUID.class));
            }
        }
        return fixture;
    }

    private static WorkerProcessHarness worker(TargetCode target, int maxInFlight, int batch) throws Exception {
        return worker(target, maxInFlight, batch, "3s", "4s");
    }

    private static WorkerProcessHarness worker(TargetCode target, int maxInFlight, int batch,
            String processingTimeout, String leaseDuration) throws Exception {
        Map<String, String> environment = common("OUTBOX_WORKER", "vra_outbox_worker", maxInFlight, batch);
        environment.put("VRA_ASYNC_PROCESSING_TIMEOUT", processingTimeout);
        environment.put("VRA_ASYNC_LEASE_DURATION", leaseDuration);
        environment.put("VRA_ASYNC_TARGET_CODE", target.name());
        if (target == TargetCode.VALIDATION_EXTERNAL_EFFECT) {
            environment.put("VRA_ASYNC_VALIDATION_MODE", "true");
            environment.put("VRA_SIMULATOR_URL", simulatorProcess.baseUri().toString());
        }
        return new WorkerProcessHarness(environment);
    }

    private static WorkerProcessHarness reconciler() throws Exception {
        Map<String, String> environment = common("RECONCILER", "vra_reconciliation_worker", 1, 1);
        environment.put("VRA_ASYNC_VALIDATION_MODE", "true");
        environment.put("VRA_SIMULATOR_URL", simulatorProcess.baseUri().toString());
        return new WorkerProcessHarness(environment);
    }

    private static Map<String, String> common(String mode, String role, int maxInFlight, int batch) {
        Map<String, String> values = new HashMap<>();
        values.put("VRA_PROCESS_MODE", mode);
        values.put("VRA_ASYNC_DB_URL", VRA.getJdbcUrl());
        values.put("VRA_ASYNC_DB_USERNAME", role);
        values.put("VRA_ASYNC_DB_PASSWORD", ASYNC_PASSWORD);
        values.put("VRA_ASYNC_LEASE_DURATION", "4s");
        values.put("VRA_ASYNC_PROCESSING_TIMEOUT", "3s");
        values.put("VRA_ASYNC_RECONCILIATION_LEASE_DURATION", "4s");
        values.put("VRA_ASYNC_RECONCILIATION_TIMEOUT", "3s");
        values.put("VRA_ASYNC_POLL_INTERVAL", "20ms");
        values.put("VRA_ASYNC_BATCH_SIZE", Integer.toString(batch));
        values.put("VRA_ASYNC_MAX_IN_FLIGHT", Integer.toString(maxInFlight));
        return values;
    }

    private static Fixture fixture(TargetCode target) throws Exception {
        UUID eventId = UUID.randomUUID();
        Payload payload = new Payload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), StockStatus.AVAILABLE, 1);
        owner("INSERT INTO vra.inventory_balance VALUES ('" + payload.skuId() + "','"
                + payload.ownerId() + "','" + payload.locationId() + "','AVAILABLE',10,1,1)");
        owner("INSERT INTO vra.inventory_reservation VALUES ('" + payload.reservationId() + "','"
                + payload.skuId() + "','" + payload.ownerId() + "','" + payload.locationId()
                + "','AVAILABLE',1,statement_timestamp())");
        try (Connection runtime = connection("vra_runtime"); PreparedStatement publish = runtime.prepareStatement(
                "SELECT vra.async_publish_reservation(?::uuid,?::uuid,?::varchar)")) {
            publish.setObject(1, eventId);
            publish.setObject(2, payload.reservationId());
            publish.setString(3, target.name());
            try (ResultSet row = publish.executeQuery()) { assertTrue(row.next()); }
        }
        return new Fixture(eventId, payload);
    }

    private static void bootstrap() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
            root = root.getParent();
            if (root == null) throw new IllegalStateException("Repository root missing");
        }
        VRA.copyFileToContainer(MountableFile.forHostPath(root.resolve("validation/poc-01/db/bootstrap.sql")),
                "/tmp/stage-i-bootstrap.sql");
        var result = VRA.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                "-v", "runtime_password=" + PASSWORD, "-f", "/tmp/stage-i-bootstrap.sql");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }

    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(VRA.getJdbcUrl(), role,
                role.equals("postgres") || role.equals("vra_runtime") ? PASSWORD : ASYNC_PASSWORD);
    }

    private static Connection lock(PostgreSQLContainer database, String statement) throws SQLException {
        Connection db = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                database.getPassword());
        db.setAutoCommit(false);
        try (Statement sql = db.createStatement()) { sql.execute(statement); }
        return db;
    }

    private static void owner(String statement) throws SQLException {
        try (Connection db = connection("postgres"); Statement sql = db.createStatement()) {
            sql.execute("SET ROLE vra_owner");
            sql.execute(statement);
        }
    }

    private static String value(String statement) throws SQLException {
        try (Connection db = connection("postgres"); Statement sql = db.createStatement();
             ResultSet row = sql.executeQuery(statement)) {
            assertTrue(row.next(), statement);
            String result = row.getString(1);
            assertFalse(row.next(), statement);
            return result;
        }
    }

    private static int count(String statement) throws SQLException { return Integer.parseInt(value(statement)); }
    private static String state(Fixture fixture) throws SQLException {
        return value("SELECT state FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId() + "'");
    }
    private static UUID token(Fixture fixture) throws SQLException {
        return UUID.fromString(value("SELECT claim_token::text FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId() + "'"));
    }
    private static UUID historyToken(Fixture fixture, String action) throws SQLException {
        return historyToken(fixture.eventId(), action);
    }
    private static UUID historyToken(UUID eventId, String action) throws SQLException {
        return UUID.fromString(value("SELECT claim_token::text FROM vra.outbox_delivery_history "
                + "WHERE event_id='" + eventId + "' AND action_code='" + action
                + "' ORDER BY history_id DESC LIMIT 1"));
    }
    private static int inboxCount(Fixture fixture) throws SQLException {
        return count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='" + fixture.eventId() + "'");
    }
    private static int projectionCount(Fixture fixture) throws SQLException {
        return count("SELECT count(*) FROM vra.reservation_projection WHERE reservation_id='"
                + fixture.payload().reservationId() + "'");
    }
    private static int processingCount() throws SQLException {
        return count("SELECT count(*) FROM vra.outbox_delivery WHERE state='PROCESSING'");
    }
    private static int readyCount() throws SQLException {
        return count("SELECT count(*) FROM vra.outbox_delivery WHERE state='READY'");
    }
    private static int claimHistoryCount() throws SQLException {
        return count("SELECT count(*) FROM vra.outbox_delivery_history "
                + "WHERE action_code IN ('CLAIM','RECLAIM')");
    }
    private static boolean activeWorkerSql(String operation) throws SQLException {
        return count("SELECT count(*) FROM pg_catalog.pg_stat_activity WHERE usename='vra_outbox_worker' "
                + "AND state='active' AND query LIKE '%" + operation + "%' ") > 0;
    }
    private static int simulatorRequestCount(Fixture fixture, String kind) throws SQLException {
        try (Connection db = DriverManager.getConnection(SIMULATOR.getJdbcUrl(), SIMULATOR.getUsername(),
                SIMULATOR.getPassword()); PreparedStatement sql = db.prepareStatement(
                "SELECT count(*) FROM public.sim_request_history WHERE operation_id=? AND kind=?")) {
            sql.setObject(1, fixture.eventId());
            sql.setString(2, kind);
            try (ResultSet row = sql.executeQuery()) { assertTrue(row.next()); return row.getInt(1); }
        }
    }
    private static boolean expired(Fixture fixture) throws SQLException { return expired(fixture.eventId()); }
    private static boolean expired(UUID eventId) throws SQLException {
        return Boolean.parseBoolean(value("SELECT (claim_until<statement_timestamp())::text "
                + "FROM vra.outbox_delivery WHERE event_id='" + eventId + "'"));
    }

    private static boolean completeWithOldToken(Fixture fixture, UUID token) throws SQLException {
        try (Connection worker = connection("vra_outbox_worker"); PreparedStatement sql = worker.prepareStatement(
                "SELECT vra.async_complete_delivery(?::uuid,?::uuid)")) {
            sql.setObject(1, fixture.eventId());
            sql.setObject(2, token);
            try (ResultSet row = sql.executeQuery()) { assertTrue(row.next()); return row.getBoolean(1); }
        }
    }

    private static boolean relinquishWithWorker(UUID eventId, UUID token) throws SQLException {
        try (Connection worker = connection("vra_outbox_worker"); PreparedStatement sql = worker.prepareStatement(
                "SELECT vra.async_relinquish_delivery(?::uuid,?::uuid)")) {
            sql.setObject(1, eventId);
            sql.setObject(2, token);
            try (ResultSet row = sql.executeQuery()) { assertTrue(row.next()); return row.getBoolean(1); }
        }
    }

    private static String retryWithOldToken(Fixture fixture, UUID token) throws SQLException {
        try (Connection worker = connection("vra_outbox_worker"); PreparedStatement sql = worker.prepareStatement(
                "SELECT vra.async_retry_delivery(?::uuid,?::uuid,'RETRYABLE_TRANSIENT'::varchar,1::bigint)")) {
            sql.setObject(1, fixture.eventId());
            sql.setObject(2, token);
            try (ResultSet row = sql.executeQuery()) { assertTrue(row.next()); return row.getString(1); }
        }
    }

    private static boolean failWithOldToken(Fixture fixture, UUID token) throws SQLException {
        try (Connection worker = connection("vra_outbox_worker"); PreparedStatement sql = worker.prepareStatement(
                "SELECT vra.async_fail_delivery(?::uuid,?::uuid,'NON_RETRYABLE'::varchar,"
                        + "'NON_RETRYABLE'::varchar)")) {
            sql.setObject(1, fixture.eventId());
            sql.setObject(2, token);
            try (ResultSet row = sql.executeQuery()) { assertTrue(row.next()); return row.getBoolean(1); }
        }
    }

    private static UUID handoffWithOldToken(Fixture fixture, UUID token) throws SQLException {
        try (Connection worker = connection("vra_outbox_worker"); PreparedStatement sql = worker.prepareStatement(
                "SELECT vra.async_handoff_unknown(?::uuid,?::uuid,'UNKNOWN_EXTERNAL_RESULT'::varchar)")) {
            sql.setObject(1, fixture.eventId());
            sql.setObject(2, token);
            try (ResultSet row = sql.executeQuery()) { assertTrue(row.next()); return row.getObject(1, UUID.class); }
        }
    }

    private static int simulatorOperationCount(Fixture fixture) throws SQLException {
        try (Connection db = DriverManager.getConnection(SIMULATOR.getJdbcUrl(), SIMULATOR.getUsername(),
                SIMULATOR.getPassword()); PreparedStatement sql = db.prepareStatement(
                "SELECT count(*) FROM public.sim_operation WHERE operation_id=?")) {
            sql.setObject(1, fixture.eventId());
            try (ResultSet row = sql.executeQuery()) { assertTrue(row.next()); return row.getInt(1); }
        }
    }

    private static void assertRelinquishTimesOut(UUID eventId, UUID token) throws SQLException {
        try (Connection worker = connection("vra_outbox_worker"); Statement settings = worker.createStatement()) {
            settings.execute("SET statement_timeout='150ms'");
            try (PreparedStatement call = worker.prepareStatement(
                    "SELECT vra.async_relinquish_delivery(?::uuid,?::uuid)")) {
                call.setObject(1, eventId);
                call.setObject(2, token);
                SQLException failure = assertThrows(SQLException.class, call::executeQuery);
                assertEquals("57014", failure.getSQLState());
            }
        }
    }

    private static void control(String command) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(simulatorProcess.baseUri().resolve("/test/gates/" + command))
                .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(2)).build();
        assertEquals(204, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode(),
                command);
    }

    private static void assertQueryBeforeSecondExecute(Fixture fixture) throws SQLException {
        List<String> order = new ArrayList<>();
        try (Connection db = DriverManager.getConnection(SIMULATOR.getJdbcUrl(), SIMULATOR.getUsername(),
                SIMULATOR.getPassword()); PreparedStatement sql = db.prepareStatement(
                "SELECT kind FROM public.sim_request_history WHERE operation_id=? ORDER BY request_id")) {
            sql.setObject(1, fixture.eventId());
            try (ResultSet rows = sql.executeQuery()) {
                while (rows.next()) order.add(rows.getString(1));
            }
        }
        assertFalse(order.isEmpty());
        assertEquals("EXECUTE", order.get(0));
        assertTrue(order.contains("OBSERVE"), order.toString());
        int secondExecute = order.subList(1, order.size()).indexOf("EXECUTE");
        assertTrue(secondExecute < 0 || order.indexOf("OBSERVE") < secondExecute + 1, order.toString());
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_event WHERE event_id='"
                + fixture.eventId() + "'"));
    }

    private static void await(String label, Condition condition, WorkerProcessHarness process) throws Exception {
        long end = System.nanoTime() + DEADLINE.toNanos();
        Throwable last = null;
        while (System.nanoTime() < end) {
            try { if (condition.satisfied()) return; }
            catch (Throwable error) { last = error; }
            if (process != null && !process.alive()) {
                throw new AssertionError(label + " child exited early: " + process.diagnostics(), last);
            }
            LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
        }
        throw new AssertionError(label + " deadline; child=" + (process == null ? "already stopped"
                : "PID=" + process.pid() + " output=" + process.diagnostics()), last);
    }
}
