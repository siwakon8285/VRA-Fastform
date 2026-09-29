package dev.vra.async;

import java.net.URI;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import dev.vra.async.adapter.out.control.JdbcAsyncControlRepository;
import dev.vra.async.adapter.out.delivery.JdbcDeliveryRepository;
import dev.vra.async.adapter.out.external.ValidationSimulatorHttpAdapter;
import dev.vra.async.adapter.out.reconciliation.JdbcReconciliationRepository;
import dev.vra.async.application.control.AsyncControlPort;
import dev.vra.async.application.delivery.DeliveryPort;
import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.application.delivery.DeliveryService;
import dev.vra.async.application.external.ExternalEffectPort;
import dev.vra.async.application.reconciliation.ReconciliationPolicy;
import dev.vra.async.application.reconciliation.ReconciliationPort;
import dev.vra.async.application.reconciliation.ReconciliationPort.Claim;
import dev.vra.async.application.reconciliation.ReconciliationService;
import dev.vra.async.bootstrap.ReconciliationLoop;
import dev.vra.async.bootstrap.ReconciliationWorkerMode;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import dev.vra.async.simulator.SimulatorProcessHarness;
import dev.vra.async.simulator.SimulatorStore;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class ReconciliationIntegrationTest {
    private static final String PASSWORD = "stage-g-disposable-only";
    private static final String ASYNC_PASSWORD = "async-disposable-test-only";
    private static final PostgreSQLContainer VRA = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static final PostgreSQLContainer SIMULATOR = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("simulator_stage_g").withUsername("simulator_test").withPassword(PASSWORD);
    private static SimulatorProcessHarness simulatorProcess;
    private static SimulatorStore simulatorStore;
    private static URI simulatorUri;
    private static ValidationSimulatorHttpAdapter external;
    private static AnnotationConfigApplicationContext workerContext;
    private static AnnotationConfigApplicationContext reconcilerContext;
    private static AnnotationConfigApplicationContext operatorContext;
    private static DeliveryService delivery;
    private static ReconciliationService reconciliation;
    private static ReconciliationPort reconciliationPort;
    private static AsyncControlPort operator;
    private static final AtomicInteger OBSERVATIONS = new AtomicInteger();

    @BeforeAll
    static void start() throws Exception {
        VRA.start();
        SIMULATOR.start();
        try {
            bootstrap("validation/poc-01/db/bootstrap.sql");
            AsyncRoleBootstrap.run(VRA);
            assertEquals(3, new MigrationRunner().migrate(VRA.getJdbcUrl(), "vra_migrator", PASSWORD));
            simulatorProcess = new SimulatorProcessHarness(SIMULATOR);
            simulatorUri = simulatorProcess.baseUri();
            simulatorStore = new SimulatorStore(SIMULATOR.getJdbcUrl(), SIMULATOR.getUsername(),
                    SIMULATOR.getPassword());
            external = new ValidationSimulatorHttpAdapter(simulatorUri, Duration.ofSeconds(2));
            workerContext = new AnnotationConfigApplicationContext(WorkerConfiguration.class);
            reconcilerContext = new AnnotationConfigApplicationContext(ReconcilerConfiguration.class);
            operatorContext = new AnnotationConfigApplicationContext(OperatorConfiguration.class);
            delivery = workerContext.getBean(DeliveryService.class);
            reconciliation = reconcilerContext.getBean(ReconciliationService.class);
            reconciliationPort = reconcilerContext.getBean(ReconciliationPort.class);
            operator = operatorContext.getBean(AsyncControlPort.class);
        } catch (Exception failure) {
            stop();
            throw failure;
        }
    }

    @AfterAll
    static void stop() {
        if (operatorContext != null) operatorContext.close();
        if (reconcilerContext != null) reconcilerContext.close();
        if (workerContext != null) workerContext.close();
        if (simulatorProcess != null) simulatorProcess.close();
        SIMULATOR.stop();
        VRA.stop();
    }

    @BeforeEach
    void clear() throws Exception {
        owner("TRUNCATE vra.consumer_inbox,vra.reservation_projection,vra.outbox_event CASCADE");
        OBSERVATIONS.set(0);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class WorkerConfiguration {
        @Bean DataSource dataSource() { return source("vra_outbox_worker"); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new JdbcTransactionManager(source);
        }
        @Bean DeliveryPort deliveryPort(DataSource source) {
            return new JdbcDeliveryRepository(JdbcClient.create(source));
        }
        @Bean DeliveryService deliveryService(DeliveryPort port) { return new DeliveryService(port); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class ReconcilerConfiguration {
        @Bean DataSource dataSource() { return source("vra_reconciliation_worker"); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new JdbcTransactionManager(source);
        }
        @Bean ReconciliationPort reconciliationPort(DataSource source) {
            return new JdbcReconciliationRepository(JdbcClient.create(source));
        }
        @Bean ExternalEffectPort externalEffectPort() {
            return new ExternalEffectPort() {
                @Override public ExecuteOutcome execute(UUID eventId, Payload payload) {
                    throw new AssertionError("Reconciler must never execute an external effect");
                }
                @Override public Observation observe(UUID eventId) {
                    try {
                        assertEquals("CHECKING", value("SELECT state FROM vra.reconciliation_case WHERE event_id='"
                                + eventId + "'")); // independent DB connection proves claim committed
                    } catch (SQLException error) {
                        throw new AssertionError(error);
                    }
                    OBSERVATIONS.incrementAndGet();
                    return external.observe(eventId);
                }
            };
        }
        @Bean ReconciliationPolicy reconciliationPolicy() {
            return new ReconciliationPolicy(Duration.ofMillis(500), Duration.ofSeconds(5),
                    Duration.ofMillis(200), Duration.ofSeconds(5), 0.20, () -> 0);
        }
        @Bean ReconciliationService reconciliationService(ReconciliationPort port,
                ExternalEffectPort external, ReconciliationPolicy policy) {
            return new ReconciliationService(port, external, policy);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class OperatorConfiguration {
        @Bean DataSource dataSource() { return source("vra_async_operator"); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new JdbcTransactionManager(source);
        }
        @Bean AsyncControlPort controlPort(DataSource source) {
            return new JdbcAsyncControlRepository(JdbcClient.create(source));
        }
    }

    @Test
    void lostResponseUnknownHandoffThenCommittedObservationConfirmsExactlyOneEffect() throws Exception {
        Fixture fixture = fixture();
        control("drop-next");
        ClaimedDelivery claim = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                "worker-g", 1, Duration.ofSeconds(30)));
        assertEquals(ExternalEffectPort.ExecuteOutcome.UNKNOWN_OUTCOME,
                external.execute(fixture.eventId, fixture.payload));
        assertEquals(1, simulatorStore.read(fixture.eventId).effectCount());
        UUID caseId = delivery.handoffUnknown(claim);
        assertNotNull(caseId);
        assertEquals("RECONCILIATION_REQUIRED:UNKNOWN_OUTCOME:UNKNOWN_EXTERNAL_RESULT",
                value("SELECT state||':'||failure_class||':'||reason_code FROM vra.outbox_delivery "
                        + "WHERE event_id='" + fixture.eventId + "'"));
        assertEquals("PENDING:UNKNOWN:1:0:4:0", value("SELECT state||':'||external_knowledge||':'||"
                + "automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit||':'||lifetime_attempt_count "
                + "FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code='UNKNOWN_HANDOFF'"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND action_code='CREATED'"));

        assertEquals(1, reconciliation.reconcileAvailable("reconciler-g", 1, Duration.ofSeconds(30)));
        assertEquals(1, OBSERVATIONS.get());
        assertEquals("RESOLVED:CONFIRMED_SUCCEEDED", value("SELECT state||':'||external_knowledge "
                + "FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
        assertEquals("SUCCEEDED", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND action_code='CONFIRMED_SUCCESS' AND observation='CONFIRMED_SUCCEEDED'"));
        assertEquals(1, simulatorStore.read(fixture.eventId).effectCount());
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='"
                + fixture.eventId + "'"));
    }

    @Test
    void strongAbsenceResolvesCaseAndSchedulesSameEventSameCycleRetry() throws Exception {
        Fixture fixture = fixture();
        ClaimedDelivery claim = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                "worker-g", 1, Duration.ofSeconds(30)));
        UUID caseId = delivery.handoffUnknown(claim);
        assertNotNull(caseId);
        assertNull(simulatorStore.read(fixture.eventId));
        int eventRows = count("SELECT count(*) FROM vra.outbox_event");
        assertEquals(1, reconciliation.reconcileAvailable("reconciler-g", 1, Duration.ofSeconds(30)));
        assertEquals("RESOLVED:CONFIRMED_NO_EFFECT", value("SELECT state||':'||external_knowledge "
                + "FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
        assertEquals("RETRY_WAIT:1:1:5", value("SELECT state||':'||automatic_cycle||':'||"
                + "cycle_claim_count||':'||cycle_claim_limit FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals("t", value("SELECT next_eligible_at > state_changed_at AND "
                + "next_eligible_at > statement_timestamp() FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(eventRows, count("SELECT count(*) FROM vra.outbox_event"));
        assertNull(simulatorStore.read(fixture.eventId));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND observation='CONFIRMED_NO_EFFECT'"));
        owner("UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-interval '1 second' "
                + "WHERE event_id='" + fixture.eventId + "'");
        ClaimedDelivery retry = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                "worker-later", 1, Duration.ofSeconds(30)));
        assertEquals(fixture.eventId, retry.eventId());
        assertEquals(1, retry.automaticCycle());
        UUID laterCase = delivery.handoffUnknown(retry);
        assertNotEquals(caseId, laterCase);
        assertEquals("RESOLVED", value("SELECT state FROM vra.reconciliation_case WHERE case_id='"
                + caseId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='"
                + fixture.eventId + "' AND state IN ('PENDING','CHECKING','WAITING','OPERATOR_REQUIRED')"));
        assertEquals(eventRows, count("SELECT count(*) FROM vra.outbox_event"));
    }

    @Test
    void pendingSimulatorObservationPersistsIndeterminateHistoryAndUnknownCaseReason() throws Exception {
        Fixture fixture = fixture();
        control("block-next");
        CompletableFuture<ExternalEffectPort.ExecuteOutcome> pending = CompletableFuture.supplyAsync(
                () -> external.execute(fixture.eventId, fixture.payload));
        try {
            awaitSimulatorState(fixture.eventId, "PENDING");
            ClaimedDelivery deliveryClaim = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                    "worker-g", 1, Duration.ofSeconds(30)));
            UUID caseId = delivery.handoffUnknown(deliveryClaim);
            assertEquals(1, reconciliation.reconcileAvailable("reconciler-g", 1, Duration.ofSeconds(30)));
            assertEquals("WAITING:UNKNOWN:UNKNOWN_EXTERNAL_RESULT:true:true:true:1:1:4:1",
                    value("SELECT state||':'||external_knowledge||':'||reason_code||':'||"
                            + "(next_eligible_at>statement_timestamp())||':'||(claim_token IS NULL)||':'||"
                            + "(claim_until IS NULL)||':'||automatic_cycle||':'||cycle_claim_count||':'||"
                            + "cycle_claim_limit||':'||lifetime_attempt_count FROM vra.reconciliation_case "
                            + "WHERE case_id='" + caseId + "'"));
            assertEquals("INDETERMINATE_WAIT:INDETERMINATE:UNKNOWN_EXTERNAL_RESULT:1:1:4:1",
                    value("SELECT action_code||':'||observation||':'||reason_code||':'||automatic_cycle||':'||"
                            + "cycle_claim_count||':'||cycle_claim_limit||':'||lifetime_attempt "
                            + "FROM vra.reconciliation_history WHERE case_id='" + caseId
                            + "' ORDER BY history_id DESC LIMIT 1"));
            assertTrue(reconciliationPort.claim("before-due", 1, Duration.ofSeconds(30)).isEmpty());
            owner("UPDATE vra.reconciliation_case SET next_eligible_at=statement_timestamp()-interval '1 second' "
                    + "WHERE case_id='" + caseId + "'");
            Claim next = onlyCase(reconciliationPort.claim("after-due", 1, Duration.ofSeconds(30)));
            String before = caseRow(caseId);
            int histories = count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='" + caseId + "'");
            try (Connection reconciler = connection("vra_reconciliation_worker"); Statement sql = reconciler.createStatement()) {
                SQLException rejected = assertThrows(SQLException.class, () -> sql.execute(
                        "SELECT vra.async_wait_reconciliation('" + caseId + "','" + next.claimToken()
                                + "','INDETERMINATE',1000::bigint)"));
                assertEquals("22023", rejected.getSQLState());
            }
            assertEquals(before, caseRow(caseId));
            assertEquals(histories, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                    + caseId + "'"));
            assertEquals("WAITING", reconciliationPort.waitIndeterminate(caseId, next.claimToken(),
                    Duration.ofMillis(500)));
        } finally {
            control("release");
        }
        assertEquals(ExternalEffectPort.ExecuteOutcome.CONFIRMED_SUCCEEDED, pending.get(5, TimeUnit.SECONDS));
    }

    @Test
    void fourIndeterminateQueriesExhaustWithoutChangingPersistedLimit() throws Exception {
        Fixture fixture = fixture();
        control("block-next");
        CompletableFuture<ExternalEffectPort.ExecuteOutcome> pending = CompletableFuture.supplyAsync(
                () -> external.execute(fixture.eventId, fixture.payload));
        try {
            awaitSimulatorState(fixture.eventId, "PENDING");
            UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                    TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
            for (int attempt = 1; attempt <= 4; attempt++) {
                assertEquals(1, reconciliation.reconcileAvailable("reconciler-g", 1, Duration.ofSeconds(30)));
                assertEquals(attempt, OBSERVATIONS.get());
                assertEquals(attempt + ":4:" + attempt, value("SELECT cycle_claim_count||':'||cycle_claim_limit||':'||"
                        + "lifetime_attempt_count FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
                if (attempt < 4) {
                    assertEquals("WAITING", value("SELECT state FROM vra.reconciliation_case WHERE case_id='"
                            + caseId + "'"));
                    owner("UPDATE vra.reconciliation_case SET next_eligible_at=statement_timestamp()-interval '1 second' "
                            + "WHERE case_id='" + caseId + "'");
                }
            }
            assertEquals("OPERATOR_REQUIRED:UNKNOWN:RECONCILIATION_EXHAUSTED:true:true:true",
                    value("SELECT state||':'||external_knowledge||':'||reason_code||':'||"
                            + "(next_eligible_at IS NULL)||':'||(claim_token IS NULL)||':'||(claim_until IS NULL) "
                            + "FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
            assertEquals("FAILED:UNKNOWN_OUTCOME:RECONCILIATION_EXHAUSTED", value("SELECT state||':'||"
                    + "failure_class||':'||reason_code FROM vra.outbox_delivery WHERE event_id='"
                    + fixture.eventId + "'"));
            assertEquals("EXHAUSTED:INDETERMINATE:RECONCILIATION_EXHAUSTED", value("SELECT action_code||':'||"
                    + "observation||':'||reason_code FROM vra.reconciliation_history WHERE case_id='"
                    + caseId + "' ORDER BY history_id DESC LIMIT 1"));
            assertTrue(reconciliationPort.claim("fifth-query", 1, Duration.ofSeconds(30)).isEmpty());
            assertEquals(4, OBSERVATIONS.get());
        } finally {
            control("release");
        }
        assertEquals(ExternalEffectPort.ExecuteOutcome.CONFIRMED_SUCCEEDED, pending.get(5, TimeUnit.SECONDS));
    }

    @Test
    void expiredFourthCheckingGetsOneRecoveryOnlyClaimAndNoObservation() throws Exception {
        Fixture fixture = fixture();
        UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        for (int attempt = 1; attempt <= 4; attempt++) {
            Claim claim = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
            assertEquals(attempt, claim.cycleClaimCount());
            assertEquals(4, claim.cycleClaimLimit());
            owner("UPDATE vra.reconciliation_case SET claim_until=statement_timestamp()-interval '1 second' "
                    + "WHERE case_id='" + caseId + "'");
        }
        assertEquals(0, reconciliation.reconcileAvailable("recovery-only", 1, Duration.ofSeconds(30)));
        assertEquals(0, OBSERVATIONS.get());
        assertEquals("OPERATOR_REQUIRED:UNKNOWN:5:5:4", value("SELECT state||':'||external_knowledge||':'||"
                + "lifetime_attempt_count||':'||cycle_claim_count||':'||cycle_claim_limit "
                + "FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
        assertEquals("FAILED:UNKNOWN_OUTCOME", value("SELECT state||':'||failure_class "
                + "FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND action_code='RECOVERY_ONLY_EXHAUSTED' AND cycle_claim_count=5"));
        assertTrue(reconciliationPort.claim("repeat-recovery", 1, Duration.ofSeconds(30)).isEmpty());
        assertEquals(0, OBSERVATIONS.get());
    }

    @Test
    void reclaimedTokenFencesEveryOldFinalizationAndCurrentTokenCanFinish() throws Exception {
        Fixture fixture = fixture();
        UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        Claim old = onlyCase(reconciliationPort.claim("c1", 1, Duration.ofSeconds(30)));
        owner("UPDATE vra.reconciliation_case SET claim_until=statement_timestamp()-interval '1 second' "
                + "WHERE case_id='" + caseId + "'");
        Claim current = onlyCase(reconciliationPort.claim("c2", 1, Duration.ofSeconds(30)));
        assertNotEquals(old.claimToken(), current.claimToken());
        String beforeCase = caseRow(caseId);
        int beforeHistory = count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='" + caseId + "'");
        assertNull(reconciliationPort.waitIndeterminate(caseId, old.claimToken(), Duration.ofMillis(1)));
        assertFalse(reconciliationPort.confirmSuccess(caseId, old.claimToken()));
        assertNull(reconciliationPort.confirmNoEffect(caseId, old.claimToken(), true, Duration.ofMillis(1)));
        assertFalse(reconciliationPort.exhaust(caseId, old.claimToken()));
        assertEquals(beforeCase, caseRow(caseId));
        assertEquals(beforeHistory, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "'"));
        assertTrue(reconciliationPort.confirmSuccess(caseId, current.claimToken()));
        assertEquals("SUCCEEDED", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
    }

    @Test
    void operatorResumeStartsNewCaseCycleWithoutErasingLifetimeOrDeliveryCycle() throws Exception {
        Fixture fixture = fixture();
        UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        Claim current = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        assertTrue(reconciliationPort.exhaust(caseId, current.claimToken()));
        String oldHistory = value("SELECT json_agg(row_to_json(h) ORDER BY history_id)::text "
                + "FROM vra.reconciliation_history h WHERE case_id='" + caseId + "'");
        assertFalse(operator.replay(fixture.eventId, "review-g"));
        assertTrue(operator.resume(caseId, "review-g"));
        assertEquals("RECONCILIATION_REQUIRED:UNKNOWN_OUTCOME:1:1:5",
                value("SELECT state||':'||failure_class||':'||automatic_cycle||':'||"
                        + "cycle_claim_count||':'||cycle_claim_limit FROM vra.outbox_delivery "
                        + "WHERE event_id='" + fixture.eventId + "'"));
        assertEquals("PENDING:UNKNOWN:2:0:4:1", value("SELECT state||':'||external_knowledge||':'||"
                + "automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit||':'||"
                + "lifetime_attempt_count FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
        assertEquals(oldHistory, value("SELECT json_agg(row_to_json(h) ORDER BY history_id)::text "
                + "FROM vra.reconciliation_history h WHERE case_id='" + caseId
                + "' AND automatic_cycle=1"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND action_code='CONTROL_RESUME' AND automatic_cycle=2 "
                + "AND cycle_claim_count=0 AND cycle_claim_limit=4 AND lifetime_attempt=1"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code='CONTROL_RESUME' AND automatic_cycle=1"));
        assertEquals(0, OBSERVATIONS.get());
        assertNull(simulatorStore.read(fixture.eventId));
        assertEquals(1, reconciliation.reconcileAvailable("after-resume", 1, Duration.ofSeconds(30)));
        assertEquals("RESOLVED:CONFIRMED_NO_EFFECT:2:1:4:2", value("SELECT state||':'||"
                + "external_knowledge||':'||automatic_cycle||':'||cycle_claim_count||':'||"
                + "cycle_claim_limit||':'||lifetime_attempt_count FROM vra.reconciliation_case "
                + "WHERE case_id='" + caseId + "'"));
    }

    @Test
    void operatorClosePreservesUnknownAndTerminallyStopsCaseAndDelivery() throws Exception {
        Fixture fixture = fixture();
        UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        Claim current = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        assertTrue(reconciliationPort.exhaust(caseId, current.claimToken()));
        assertTrue(operator.close(fixture.eventId, "review-close"));
        assertEquals("CLOSED:UNKNOWN_OUTCOME:CONTROLLED_CLOSE", value("SELECT state||':'||"
                + "failure_class||':'||reason_code FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals("CLOSED:UNKNOWN:CONTROLLED_CLOSE", value("SELECT state||':'||"
                + "external_knowledge||':'||reason_code FROM vra.reconciliation_case WHERE case_id='"
                + caseId + "'"));
        assertFalse(operator.close(fixture.eventId, "again"));
        assertFalse(operator.resume(caseId, "again"));
        assertFalse(operator.replay(fixture.eventId, "again"));
        assertTrue(reconciliationPort.claim("after-close", 1, Duration.ofSeconds(30)).isEmpty());
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND action_code='CONTROL_CLOSE' AND reason_code='CONTROLLED_CLOSE'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code='CONTROL_CLOSE' AND reason_code='CONTROLLED_CLOSE'"));
        assertEquals(0, OBSERVATIONS.get());
        assertNull(simulatorStore.read(fixture.eventId));
    }

    @Test
    void explicitExhaustionLeavesUnknownAndOperatorCanCloseFailedWorkWithoutCase() throws Exception {
        Fixture uncertain = fixture();
        UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        Claim claim = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        assertTrue(reconciliationPort.exhaust(caseId, claim.claimToken()));
        assertEquals("OPERATOR_REQUIRED:UNKNOWN:RECONCILIATION_EXHAUSTED", value("SELECT state||':'||"
                + "external_knowledge||':'||reason_code FROM vra.reconciliation_case WHERE case_id='"
                + caseId + "'"));
        assertEquals("FAILED:UNKNOWN_OUTCOME:RECONCILIATION_EXHAUSTED", value("SELECT state||':'||"
                + "failure_class||':'||reason_code FROM vra.outbox_delivery WHERE event_id='"
                + uncertain.eventId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND action_code='EXHAUSTED' AND observation IS NULL"));
        assertFalse(reconciliationPort.exhaust(caseId, claim.claimToken()));

        Fixture ordinary = projectionFixture();
        ClaimedDelivery workerClaim = only(delivery.claim(TargetCode.RESERVATION_PROJECTION,
                "worker-g", 1, Duration.ofSeconds(30)));
        assertTrue(delivery.failNonRetryable(workerClaim));
        assertEquals(0, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='"
                + ordinary.eventId + "'"));
        assertTrue(operator.close(ordinary.eventId, "review-ordinary"));
        assertEquals("CLOSED:NON_RETRYABLE:CONTROLLED_CLOSE", value("SELECT state||':'||"
                + "failure_class||':'||reason_code FROM vra.outbox_delivery WHERE event_id='"
                + ordinary.eventId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='"
                + ordinary.eventId + "'"));
    }

    @Test
    void activeCaseConflictAbortsSecondHandoffWithoutPartialMutation() throws Exception {
        Fixture fixture = fixture();
        UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        String deliveryBefore = deliveryRow(fixture.eventId);
        String caseBefore = caseRow(caseId);
        int historyBefore = count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "'");
        try (Connection db = connection("postgres"); Statement sql = db.createStatement()) {
            db.setAutoCommit(false);
            sql.execute("SET ROLE vra_owner");
            UUID syntheticToken = UUID.randomUUID();
            sql.execute("UPDATE vra.outbox_delivery SET state='PROCESSING',failure_class=NULL,reason_code=NULL,"
                    + "claim_token='" + syntheticToken + "',claim_until=statement_timestamp()+interval '30 seconds' "
                    + "WHERE event_id='" + fixture.eventId + "'");
            sql.execute("SET ROLE vra_outbox_worker");
            SQLException conflict = assertThrows(SQLException.class, () -> sql.execute(
                    "SELECT vra.async_handoff_unknown('" + fixture.eventId + "','" + syntheticToken
                            + "','UNKNOWN_EXTERNAL_RESULT')"));
            assertEquals("23505", conflict.getSQLState());
            db.rollback();
        }
        assertEquals(deliveryBefore, deliveryRow(fixture.eventId));
        assertEquals(caseBefore, caseRow(caseId));
        assertEquals(historyBefore, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='"
                + fixture.eventId + "'"));
    }

    @Test
    void operatorResumeAndCloseRaceToOnePairedTransitionWhileReplayIsDenied() throws Exception {
        Fixture fixture = fixture();
        UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        Claim claim = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        assertTrue(reconciliationPort.exhaust(caseId, claim.claimToken()));
        CompletableFuture<Boolean> resume = CompletableFuture.supplyAsync(
                () -> operator.resume(caseId, "race-resume"));
        CompletableFuture<Boolean> close = CompletableFuture.supplyAsync(
                () -> operator.close(fixture.eventId, "race-close"));
        assertNotEquals(resume.get(5, TimeUnit.SECONDS), close.get(5, TimeUnit.SECONDS));
        assertFalse(operator.replay(fixture.eventId, "race-replay"));
        String state = value("SELECT d.state||':'||c.state||':'||c.external_knowledge "
                + "FROM vra.outbox_delivery d JOIN vra.reconciliation_case c USING(event_id) "
                + "WHERE d.event_id='" + fixture.eventId + "'");
        assertTrue(state.equals("RECONCILIATION_REQUIRED:PENDING:UNKNOWN")
                || state.equals("CLOSED:CLOSED:UNKNOWN"), state);
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code IN ('CONTROL_RESUME','CONTROL_CLOSE')"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND action_code IN ('CONTROL_RESUME','CONTROL_CLOSE')"));
    }

    @Test
    void actualCredentialsEnforceReconcilerOperatorWorkerSeparation() throws Exception {
        Fixture fixture = fixture();
        ClaimedDelivery workerClaim = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                "worker-g", 1, Duration.ofSeconds(30)));
        UUID caseId = delivery.handoffUnknown(workerClaim);
        Claim caseClaim = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        String beforeCase = caseRow(caseId);
        for (String sql : List.of(
                "SELECT * FROM vra.async_claim_delivery('VALIDATION_EXTERNAL_EFFECT','recon',1,1000::bigint)",
                "SELECT vra.async_complete_delivery('" + fixture.eventId + "','" + workerClaim.claimToken() + "')",
                "SELECT vra.async_control_replay('" + fixture.eventId + "','CONTROLLED_REPLAY','recon')",
                "SELECT vra.async_control_resume('" + caseId + "','CONTROLLED_RESUME','recon')",
                "SELECT vra.async_control_close('" + fixture.eventId + "','CONTROLLED_CLOSE','recon')",
                "UPDATE vra.reconciliation_case SET cycle_claim_limit=16 WHERE case_id='" + caseId + "'",
                "UPDATE vra.outbox_delivery SET state='FAILED' WHERE event_id='" + fixture.eventId + "'",
                "UPDATE vra.inventory_balance SET reserved=reserved",
                "SET ROLE vra_owner", "SET ROLE vra_async_executor")) {
            denied("vra_reconciliation_worker", sql);
        }
        for (String sql : List.of(
                "SELECT * FROM vra.async_claim_reconciliation('worker',1,1000::bigint)",
                "SELECT vra.async_wait_reconciliation('" + caseId + "','" + caseClaim.claimToken()
                        + "','UNKNOWN_EXTERNAL_RESULT',1000::bigint)",
                "SELECT vra.async_confirm_external_success('" + caseId + "','" + caseClaim.claimToken() + "')",
                "SELECT vra.async_confirm_external_no_effect('" + caseId + "','" + caseClaim.claimToken()
                        + "',true,1000::bigint)",
                "SELECT vra.async_exhaust_reconciliation('" + caseId + "','" + caseClaim.claimToken()
                        + "','RECONCILIATION_EXHAUSTED')")) {
            denied("vra_outbox_worker", sql);
            denied("vra_async_operator", sql);
        }
        for (String sql : List.of(
                "SELECT * FROM vra.async_claim_delivery('VALIDATION_EXTERNAL_EFFECT','operator',1,1000::bigint)",
                "UPDATE vra.reconciliation_case SET state='RESOLVED' WHERE case_id='" + caseId + "'",
                "UPDATE vra.outbox_delivery SET state='SUCCEEDED' WHERE event_id='" + fixture.eventId + "'",
                "UPDATE vra.inventory_balance SET reserved=reserved",
                "SET ROLE vra_owner", "SET ROLE vra_async_executor")) {
            denied("vra_async_operator", sql);
        }
        assertEquals(beforeCase, caseRow(caseId));
        assertTrue(reconciliationPort.confirmSuccess(caseId, caseClaim.claimToken()));
    }

    @Test
    void requiredHistoryFailuresRollBackAllSevenPairedTransitions() throws Exception {
        Fixture unknown = fixture();
        ClaimedDelivery deliveryClaim = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                "worker-g", 1, Duration.ofSeconds(30)));
        assertHistoryRollback("CREATED", unknown.eventId, () -> delivery.handoffUnknown(deliveryClaim));
        UUID unknownCase = delivery.handoffUnknown(deliveryClaim);
        Claim successClaim = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        assertHistoryRollback("CONFIRMED_SUCCESS", unknown.eventId,
                () -> reconciliationPort.confirmSuccess(unknownCase, successClaim.claimToken()));
        assertTrue(reconciliationPort.confirmSuccess(unknownCase, successClaim.claimToken()));

        Fixture noEffect = fixture();
        UUID noEffectCase = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        Claim noEffectClaim = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        assertHistoryRollback("CONFIRMED_NO_EFFECT", noEffect.eventId,
                () -> reconciliationPort.confirmNoEffect(noEffectCase, noEffectClaim.claimToken(),
                        true, Duration.ofMillis(200)));

        Fixture waiting = fixture();
        UUID waitingCase = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        for (int attempt = 1; attempt <= 3; attempt++) {
            Claim claim = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
            assertEquals("WAITING", reconciliationPort.waitIndeterminate(waitingCase, claim.claimToken(),
                    Duration.ofMillis(500)));
            owner("UPDATE vra.reconciliation_case SET next_eligible_at=statement_timestamp()-interval '1 second' "
                    + "WHERE case_id='" + waitingCase + "'");
        }
        Claim last = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        assertHistoryRollback("EXHAUSTED", waiting.eventId,
                () -> reconciliationPort.waitIndeterminate(waitingCase, last.claimToken(), Duration.ofMillis(1)));

        Fixture exhausted = fixture();
        UUID exhaustedCase = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        Claim explicit = onlyCase(reconciliationPort.claim("reconciler-g", 1, Duration.ofSeconds(30)));
        assertHistoryRollback("EXHAUSTED", exhausted.eventId,
                () -> reconciliationPort.exhaust(exhaustedCase, explicit.claimToken()));
        assertTrue(reconciliationPort.exhaust(exhaustedCase, explicit.claimToken()));
        assertHistoryRollback("CONTROL_RESUME", exhausted.eventId,
                () -> operator.resume(exhaustedCase, "history-fixture"));
        assertHistoryRollback("CONTROL_CLOSE", exhausted.eventId,
                () -> operator.close(exhausted.eventId, "history-fixture"));
    }

    @Test
    void exhaustedDeliveryCycleResolvesNoEffectWithoutOpeningAnotherRetry() throws Exception {
        Fixture fixture = fixture();
        for (int attempt = 1; attempt < 5; attempt++) {
            ClaimedDelivery old = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                    "worker-g", 1, Duration.ofSeconds(30)));
            assertTrue(delivery.relinquish(old));
        }
        ClaimedDelivery finalClaim = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                "worker-g", 1, Duration.ofSeconds(30)));
        assertEquals(5, finalClaim.cycleClaimCount());
        UUID caseId = delivery.handoffReclaimedExternal(finalClaim);
        assertEquals(1, reconciliation.reconcileAvailable("reconciler-g", 1, Duration.ofSeconds(30)));
        assertEquals("RESOLVED:CONFIRMED_NO_EFFECT", value("SELECT state||':'||external_knowledge "
                + "FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
        assertEquals("FAILED:OPERATOR_REQUIRED:CONFIRMED_NO_EFFECT:1:5:5", value("SELECT state||':'||"
                + "failure_class||':'||reason_code||':'||automatic_cycle||':'||cycle_claim_count||':'||"
                + "cycle_claim_limit FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_event WHERE event_id='"
                + fixture.eventId + "'"));
    }

    @Test
    void recreatedRepositoryRediscoversDueWaitingAndExpiredCheckingWithoutLimitExpansion() throws Exception {
        Fixture fixture = fixture();
        UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
        Claim first = onlyCase(reconciliationPort.claim("first-instance", 1, Duration.ofSeconds(30)));
        assertEquals("WAITING", reconciliationPort.waitIndeterminate(caseId, first.claimToken(),
                Duration.ofSeconds(5)));
        String checkpoint = value("SELECT automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit||':'||"
                + "lifetime_attempt_count FROM vra.reconciliation_case WHERE case_id='" + caseId + "'");
        try (AnnotationConfigApplicationContext recreated = new AnnotationConfigApplicationContext(
                ReconcilerConfiguration.class)) {
            ReconciliationPort fresh = recreated.getBean(ReconciliationPort.class);
            assertTrue(fresh.claim("before-due", 1, Duration.ofSeconds(30)).isEmpty());
            assertEquals(checkpoint, value("SELECT automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit||':'||"
                    + "lifetime_attempt_count FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
            owner("UPDATE vra.reconciliation_case SET next_eligible_at=statement_timestamp()-interval '1 second' "
                    + "WHERE case_id='" + caseId + "'");
            Claim second = onlyCase(fresh.claim("second-instance", 1, Duration.ofSeconds(30)));
            assertEquals(2, second.cycleClaimCount());
            assertEquals(4, second.cycleClaimLimit());
            owner("UPDATE vra.reconciliation_case SET claim_until=statement_timestamp()-interval '1 second' "
                    + "WHERE case_id='" + caseId + "'");
            Claim third = onlyCase(fresh.claim("reclaim-instance", 1, Duration.ofSeconds(30)));
            assertEquals(3, third.cycleClaimCount());
            assertEquals(4, third.cycleClaimLimit());
            assertNotEquals(second.claimToken(), third.claimToken());
            assertFalse(fresh.confirmSuccess(caseId, second.claimToken()));
            assertEquals("WAITING", fresh.waitIndeterminate(caseId, third.claimToken(),
                    Duration.ofMillis(500)));
        }
        assertEquals("1:3:4:3", value("SELECT automatic_cycle||':'||cycle_claim_count||':'||"
                + "cycle_claim_limit||':'||lifetime_attempt_count FROM vra.reconciliation_case WHERE case_id='"
                + caseId + "'"));
    }

    @Test
    void reconcilerModeUsesOnlyItsCredentialAndNonWebBeans() throws Exception {
        Map<String, String> settings = Map.of("VRA_ASYNC_DB_URL", VRA.getJdbcUrl(),
                "VRA_ASYNC_DB_USERNAME", "vra_reconciliation_worker",
                "VRA_ASYNC_DB_PASSWORD", ASYNC_PASSWORD,
                "VRA_ASYNC_VALIDATION_MODE", "true",
                "VRA_SIMULATOR_URL", simulatorUri.toString());
        try (AnnotationConfigApplicationContext mode = ReconciliationWorkerMode.start(settings)) {
            assertEquals("vra_reconciliation_worker", mode.getBean(JdbcClient.class)
                    .sql("SELECT current_user").query(String.class).single());
            assertNotNull(mode.getBean(ReconciliationService.class));
            assertNotNull(mode.getBean(ReconciliationLoop.class));
            assertTrue(mode.getBeansOfType(DeliveryPort.class).isEmpty());
            assertTrue(mode.getBeansOfType(AsyncControlPort.class).isEmpty());
            assertEquals(0, mode.getBeanNamesForAnnotation(
                    org.springframework.web.bind.annotation.RestController.class).length);
            Fixture fixture = fixture();
            UUID caseId = delivery.handoffUnknown(only(delivery.claim(
                    TargetCode.VALIDATION_EXTERNAL_EFFECT, "worker-g", 1, Duration.ofSeconds(30))));
            assertEquals(1, mode.getBean(ReconciliationLoop.class).pollOnce());
            assertEquals("RESOLVED:CONFIRMED_NO_EFFECT", value("SELECT state||':'||external_knowledge "
                    + "FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
            assertNull(simulatorStore.read(fixture.eventId));
        }
        var wrong = new java.util.HashMap<>(settings);
        wrong.put("VRA_ASYNC_DB_USERNAME", "vra_outbox_worker");
        assertThrows(IllegalStateException.class, () -> ReconciliationWorkerMode.start(wrong));
        var invalidTiming = new java.util.HashMap<>(settings);
        invalidTiming.put("VRA_ASYNC_RECONCILIATION_TIMEOUT", "40s");
        assertThrows(IllegalArgumentException.class, () -> ReconciliationWorkerMode.start(invalidTiming));
        var invalidBudget = new java.util.HashMap<>(settings);
        invalidBudget.put("VRA_ASYNC_MAX_RECONCILIATION_CLAIMS_PER_CYCLE", "8");
        assertThrows(IllegalArgumentException.class, () -> ReconciliationWorkerMode.start(invalidBudget));
    }

    private static void assertHistoryRollback(String action, UUID eventId, Runnable transition) throws Exception {
        String before = pairedSnapshot(eventId);
        owner("CREATE FUNCTION vra.stage_g_reject_history() RETURNS trigger LANGUAGE plpgsql "
                + "AS $$ BEGIN RAISE EXCEPTION 'test history rejection' USING ERRCODE='23514'; END $$");
        owner("CREATE TRIGGER stage_g_reject_history BEFORE INSERT ON vra.reconciliation_history "
                + "FOR EACH ROW WHEN (NEW.action_code='" + action + "') "
                + "EXECUTE FUNCTION vra.stage_g_reject_history()");
        try {
            assertThrows(RuntimeException.class, transition::run);
            assertEquals(before, pairedSnapshot(eventId));
        } finally {
            owner("DROP TRIGGER stage_g_reject_history ON vra.reconciliation_history");
            owner("DROP FUNCTION vra.stage_g_reject_history()");
        }
    }

    private static String pairedSnapshot(UUID eventId) throws SQLException {
        return deliveryRow(eventId) + "|" + value("SELECT coalesce(json_agg(row_to_json(c) ORDER BY case_id)::text,'[]') "
                + "FROM vra.reconciliation_case c WHERE event_id='" + eventId + "'") + "|"
                + count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + eventId + "'")
                + "|" + count("SELECT count(*) FROM vra.reconciliation_history h JOIN "
                + "vra.reconciliation_case c USING(case_id) WHERE c.event_id='" + eventId + "'");
    }

    private static Claim onlyCase(List<Claim> claims) {
        assertEquals(1, claims.size());
        return claims.getFirst();
    }

    private static String caseRow(UUID caseId) throws SQLException {
        return value("SELECT row_to_json(c)::text FROM vra.reconciliation_case c WHERE case_id='" + caseId + "'");
    }

    private static void awaitSimulatorState(UUID eventId, String state) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var row = simulatorStore.read(eventId);
            if (row != null && state.equals(row.state())) return;
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        fail("Simulator did not reach " + state);
    }

    private static ClaimedDelivery only(List<ClaimedDelivery> claims) {
        assertEquals(1, claims.size());
        return claims.getFirst();
    }

    private record Fixture(UUID eventId, Payload payload) {}

    private static Fixture fixture() throws Exception { return fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT); }

    private static Fixture projectionFixture() throws Exception { return fixture(TargetCode.RESERVATION_PROJECTION); }

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
            try (ResultSet rows = publish.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(eventId, rows.getObject(1, UUID.class));
            }
        }
        return new Fixture(eventId, payload);
    }

    private static DataSource source(String role) {
        return new DriverManagerDataSource(VRA.getJdbcUrl(), role, ASYNC_PASSWORD);
    }

    private static void owner(String sql) throws SQLException {
        try (Connection admin = connection("postgres"); Statement statement = admin.createStatement()) {
            statement.execute("SET ROLE vra_owner");
            statement.execute(sql);
        }
    }

    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(VRA.getJdbcUrl(), role,
                role.equals("postgres") || role.equals("vra_runtime") ? PASSWORD : ASYNC_PASSWORD);
    }

    private static String value(String sql) throws SQLException {
        try (Connection admin = connection("postgres"); Statement statement = admin.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            String result = rows.getString(1);
            assertFalse(rows.next(), sql);
            return result;
        }
    }

    private static int count(String sql) throws SQLException { return Integer.parseInt(value(sql)); }

    private static String deliveryRow(UUID eventId) throws SQLException {
        return value("SELECT row_to_json(d)::text FROM vra.outbox_delivery d WHERE event_id='" + eventId + "'");
    }

    private static void denied(String role, String sql) throws SQLException {
        try (Connection db = connection(role); Statement statement = db.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute(sql), sql);
            assertEquals("42501", failure.getSQLState(), sql);
        }
    }

    private static void control(String action) throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                simulatorUri.resolve("/test/gates/" + action)).timeout(Duration.ofSeconds(2))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
    }

    private static void bootstrap(String script) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) root = root.getParent();
        VRA.copyFileToContainer(MountableFile.forHostPath(root.resolve(script)), "/tmp/stage-g-bootstrap.sql");
        var result = VRA.execInContainer("psql", "-U", "postgres", "-d", VRA.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                "-v", "runtime_password=" + PASSWORD, "-f", "/tmp/stage-g-bootstrap.sql");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }
}
