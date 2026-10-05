package dev.vra.async;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import dev.vra.async.adapter.out.external.ValidationSimulatorHttpAdapter;
import dev.vra.async.application.external.ExternalEffectPort.ExecuteOutcome;
import dev.vra.async.application.external.ExternalEffectPort.Observation;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import dev.vra.async.simulator.SimulatorProcessHarness;
import dev.vra.async.simulator.SimulatorStore;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class ValidationSimulatorIntegrationTest {
    private static final String PASSWORD = "stage-f-disposable-only";
    private static final PostgreSQLContainer VRA = new dev.vra.poc04.external.RunOwnedPostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static final PostgreSQLContainer SIMULATOR = new dev.vra.poc04.external.RunOwnedPostgreSQLContainer("postgres:17.11")
            .withDatabaseName("simulator_stage_f").withUsername("simulator_test").withPassword(PASSWORD);
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static SimulatorProcessHarness process;
    private static SimulatorStore store;
    private static URI base;
    private static ValidationSimulatorHttpAdapter adapter;

    @BeforeAll
    static void start() throws Exception {
        VRA.start();
        SIMULATOR.start();
        try {
            assertNotEquals(VRA.getJdbcUrl(), SIMULATOR.getJdbcUrl());
            bootstrapVra();
            AsyncRoleBootstrap.run(VRA);
            assertEquals(4, new MigrationRunner().migrate(VRA.getJdbcUrl(), "vra_migrator", PASSWORD));
            process = new SimulatorProcessHarness(SIMULATOR);
            assertTrue(process.alive());
            assertNotEquals(ProcessHandle.current().pid(), process.pid());
            base = process.baseUri();
            store = new SimulatorStore(SIMULATOR.getJdbcUrl(), SIMULATOR.getUsername(), SIMULATOR.getPassword());
            adapter = new ValidationSimulatorHttpAdapter(base, Duration.ofSeconds(2));
            assertEquals(200, get("/health").statusCode());
            try (Connection db = DriverManager.getConnection(SIMULATOR.getJdbcUrl(),
                    SIMULATOR.getUsername(), SIMULATOR.getPassword()); Statement sql = db.createStatement();
                 ResultSet rows = sql.executeQuery("SELECT count(*) FROM information_schema.schemata "
                         + "WHERE schema_name='vra'")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        } catch (Exception failure) {
            stop();
            throw failure;
        }
    }

    @AfterAll
    static void stop() {
        if (process != null) process.close();
        SIMULATOR.stop();
        VRA.stop();
    }

    @Test
    void validationTargetBootstrapProducesOnlyOneScenarioBDelivery() throws Exception {
        UUID eventId = UUID.randomUUID();
        Payload payload = payload();
        try (Connection admin = DriverManager.getConnection(VRA.getJdbcUrl(), "postgres", PASSWORD);
             Statement sql = admin.createStatement()) {
            sql.execute("SET ROLE vra_owner");
            sql.execute("INSERT INTO vra.inventory_balance VALUES ('" + payload.skuId() + "','"
                    + payload.ownerId() + "','" + payload.locationId() + "','AVAILABLE',10,1,1)");
            sql.execute("INSERT INTO vra.inventory_reservation VALUES ('" + payload.reservationId() + "','"
                    + payload.skuId() + "','" + payload.ownerId() + "','" + payload.locationId()
                    + "','AVAILABLE',1,statement_timestamp())");
        }
        try (Connection runtime = DriverManager.getConnection(VRA.getJdbcUrl(), "vra_runtime", PASSWORD);
             Statement sql = runtime.createStatement()) {
            sql.execute("SELECT vra.async_publish_reservation('" + eventId + "','"
                    + payload.reservationId() + "','VALIDATION_EXTERNAL_EFFECT')");
        }
        try (Connection admin = DriverManager.getConnection(VRA.getJdbcUrl(), "postgres", PASSWORD);
             Statement sql = admin.createStatement(); ResultSet row = sql.executeQuery(
                     "SELECT d.target_code,count(*) OVER () FROM vra.outbox_delivery d "
                             + "WHERE d.event_id='" + eventId + "'")) {
            assertTrue(row.next());
            assertEquals("VALIDATION_EXTERNAL_EFFECT", row.getString(1));
            assertEquals(1, row.getInt(2));
            assertFalse(row.next());
        }
        assertNull(store.read(eventId));
    }

    @Test
    void sameIdentityAndSemanticPayloadDeduplicateWhileConflictDoesNotMutate() throws Exception {
        UUID eventId = UUID.randomUUID();
        Payload payload = payload();
        String expectedDigest = SimulatorStore.digest(payload);
        assertEquals(ExecuteOutcome.CONFIRMED_SUCCEEDED, adapter.execute(eventId, payload));
        SimulatorStore.Snapshot first = store.read(eventId);
        assertNotNull(first);
        assertEquals(eventId, first.operationId());
        assertEquals(expectedDigest, first.digest());
        assertEquals("SUCCEEDED", first.state());
        assertEquals("SUCCEEDED", first.result());
        assertEquals(1, first.effectCount());
        assertEquals(ExecuteOutcome.CONFIRMED_SUCCEEDED, adapter.execute(eventId, payload));
        assertEquals(200, post("/execute", reorderedJson(eventId, payload, payload.quantity())).statusCode());
        assertEquals(first, store.read(eventId));
        assertEquals(ExecuteOutcome.CONFLICT, adapter.execute(eventId, new Payload(payload.reservationId(),
                payload.skuId(), payload.ownerId(), payload.locationId(), payload.stockStatus(),
                payload.quantity() + 1)));
        assertEquals(409, post("/execute", reorderedJson(eventId, payload, payload.quantity() + 1)).statusCode());
        assertNotEquals(expectedDigest, SimulatorStore.digest(new Payload(payload.reservationId(),
                payload.skuId(), payload.ownerId(), payload.locationId(), payload.stockStatus(),
                payload.quantity() + 1)));
        assertEquals(first, store.read(eventId));
        assertTrue(get("/test/effects/" + eventId).body().contains("\"effectCount\":1"));
        assertEquals(Observation.CONFIRMED_SUCCEEDED, observeWithoutMutation(eventId));
    }

    @Test
    void acceptedPendingIsIndeterminateAndObservationNeverCreatesAnEffect() throws Exception {
        UUID absent = UUID.randomUUID();
        assertEquals(Observation.CONFIRMED_NO_EFFECT, observeWithoutMutation(absent));
        assertNull(store.read(absent));

        UUID eventId = UUID.randomUUID();
        Payload payload = payload();
        control("block-next");
        CompletableFuture<ExecuteOutcome> first = CompletableFuture.supplyAsync(
                () -> adapter.execute(eventId, payload));
        try {
            SimulatorStore.Snapshot pending = awaitState(eventId, "PENDING");
            assertEquals(SimulatorStore.digest(payload), pending.digest());
            assertEquals(0, pending.effectCount());
            assertEquals(Observation.INDETERMINATE, observeWithoutMutation(eventId));
            assertEquals(pending, store.read(eventId));
            assertEquals(ExecuteOutcome.UNKNOWN_OUTCOME, adapter.execute(eventId, payload));
            assertEquals(pending, store.read(eventId));
        } finally {
            control("release");
        }
        assertEquals(ExecuteOutcome.CONFIRMED_SUCCEEDED, first.get(5, TimeUnit.SECONDS));
        assertEquals(1, awaitState(eventId, "SUCCEEDED").effectCount());
        assertEquals(Observation.CONFIRMED_SUCCEEDED, observeWithoutMutation(eventId));
    }

    @Test
    void observationCannotConfirmAbsenceWhileExecuteWaitsToRegister() throws Exception {
        UUID eventId = UUID.randomUUID();
        Payload payload = payload();
        int rowsBefore = operationCount();
        ValidationSimulatorHttpAdapter longClient = new ValidationSimulatorHttpAdapter(base,
                Duration.ofSeconds(5));
        ValidationSimulatorHttpAdapter shortClient = new ValidationSimulatorHttpAdapter(base,
                Duration.ofMillis(150));
        CompletableFuture<ExecuteOutcome> first;
        try (Connection barrier = DriverManager.getConnection(SIMULATOR.getJdbcUrl(),
                SIMULATOR.getUsername(), SIMULATOR.getPassword());
             Statement sql = barrier.createStatement()) {
            barrier.setAutoCommit(false);
            sql.execute("LOCK TABLE public.sim_operation IN SHARE MODE");
            first = CompletableFuture.supplyAsync(() -> longClient.execute(eventId, payload));
            awaitBlockedRegistration();
            assertNull(store.read(eventId));
            assertEquals(Observation.INDETERMINATE, shortClient.observe(eventId));
            assertNull(store.read(eventId));
            assertEquals(rowsBefore, operationCount());
        }
        assertEquals(ExecuteOutcome.CONFIRMED_SUCCEEDED, first.get(8, TimeUnit.SECONDS));
        SimulatorStore.Snapshot completed = awaitState(eventId, "SUCCEEDED");
        assertEquals(eventId, completed.operationId());
        assertEquals(SimulatorStore.digest(payload), completed.digest());
        assertEquals(1, completed.effectCount());
        assertEquals(rowsBefore + 1, operationCount());
        assertEquals(Observation.CONFIRMED_SUCCEEDED, observeWithoutMutation(eventId));
    }

    @Test
    void lostResponseAndTimeoutRemainUnknownDespiteOneDurableEffect() throws Exception {
        UUID dropped = UUID.randomUUID();
        Payload payload = payload();
        control("drop-next");
        assertEquals(ExecuteOutcome.UNKNOWN_OUTCOME, adapter.execute(dropped, payload));
        SimulatorStore.Snapshot committed = awaitState(dropped, "SUCCEEDED");
        assertEquals(1, committed.effectCount());
        ValidationSimulatorHttpAdapter restartedClient = new ValidationSimulatorHttpAdapter(base,
                Duration.ofSeconds(2));
        assertEquals(Observation.CONFIRMED_SUCCEEDED, restartedClient.observe(dropped));
        assertEquals(ExecuteOutcome.CONFIRMED_SUCCEEDED, restartedClient.execute(dropped, payload));
        assertEquals(committed, store.read(dropped));
        assertTrue(process.alive());

        UUID timedOut = UUID.randomUUID();
        control("block-next");
        ValidationSimulatorHttpAdapter shortClient = new ValidationSimulatorHttpAdapter(base,
                Duration.ofMillis(150));
        CompletableFuture<ExecuteOutcome> blocked = CompletableFuture.supplyAsync(
                () -> shortClient.execute(timedOut, payload));
        try {
            assertEquals(0, awaitState(timedOut, "PENDING").effectCount());
            assertEquals(ExecuteOutcome.UNKNOWN_OUTCOME, blocked.get(5, TimeUnit.SECONDS));
            assertEquals(Observation.INDETERMINATE, observeWithoutMutation(timedOut));
        } finally {
            control("release");
        }
        assertEquals(1, awaitState(timedOut, "SUCCEEDED").effectCount());
    }

    private static Observation observeWithoutMutation(UUID eventId) throws Exception {
        SimulatorStore.Snapshot before = store.read(eventId);
        int rowsBefore = operationCount();
        Observation result = adapter.observe(eventId);
        assertEquals(before, store.read(eventId));
        assertEquals(rowsBefore, operationCount());
        return result;
    }

    private static int operationCount() throws Exception {
        try (Connection db = DriverManager.getConnection(SIMULATOR.getJdbcUrl(),
                SIMULATOR.getUsername(), SIMULATOR.getPassword());
             Statement sql = db.createStatement();
             ResultSet rows = sql.executeQuery("SELECT count(*) FROM public.sim_operation")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static SimulatorStore.Snapshot awaitState(UUID eventId, String state) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            SimulatorStore.Snapshot row = store.read(eventId);
            if (row != null && row.state().equals(state)) return row;
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        fail("Simulator did not reach " + state + " for " + eventId);
        return null;
    }

    private static void awaitBlockedRegistration() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            try (Connection db = DriverManager.getConnection(SIMULATOR.getJdbcUrl(),
                    SIMULATOR.getUsername(), SIMULATOR.getPassword());
                 Statement sql = db.createStatement();
                 ResultSet rows = sql.executeQuery("SELECT count(*) FROM pg_stat_activity "
                         + "WHERE datname=current_database() AND query LIKE "
                         + "'INSERT INTO public.sim_operation%' AND wait_event_type='Lock'")) {
                assertTrue(rows.next());
                if (rows.getInt(1) == 1) return;
            }
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        fail("Execute INSERT did not block behind the test lock");
    }

    private static void control(String action) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        do {
            int status = post("/test/gates/" + action, "").statusCode();
            if (status == 204) return;
            assertEquals(409, status);
            if (!action.equals("release")) fail("Simulator gate rejected " + action);
            LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
        } while (System.nanoTime() < deadline);
        fail("Simulator gate did not accept " + action);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(2)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String reorderedJson(UUID eventId, Payload p, long quantity) {
        return ("{ \"payload\":{\"quantity\":%d, \"stockStatus\":\"%s\", \"locationId\":\"%s\", "
                + "\"ownerId\":\"%s\", \"skuId\":\"%s\", \"reservationId\":\"%s\"},"
                + "\"eventId\":\"%s\" }").formatted(quantity, p.stockStatus(), p.locationId(),
                        p.ownerId(), p.skuId(), p.reservationId(), eventId);
    }

    private static Payload payload() {
        return new Payload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), StockStatus.AVAILABLE, 1);
    }

    private static void bootstrapVra() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) root = root.getParent();
        VRA.copyFileToContainer(MountableFile.forHostPath(root.resolve("validation/poc-01/db/bootstrap.sql")),
                "/tmp/stage-f-bootstrap.sql");
        var result = VRA.execInContainer("psql", "-U", "postgres", "-d", VRA.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                "-v", "runtime_password=" + PASSWORD, "-f", "/tmp/stage-f-bootstrap.sql");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }
}
