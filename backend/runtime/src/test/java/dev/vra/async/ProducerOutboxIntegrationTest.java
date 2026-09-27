package dev.vra.async;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.vra.inventory.application.IdempotentReservationApplicationService;
import dev.vra.inventory.application.IdempotentReservationResult;
import dev.vra.inventory.application.IdempotentReserveInventoryCommand;
import dev.vra.inventory.application.ReservationApplicationService;
import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationFailureException;
import dev.vra.inventory.application.ReservationResult;
import dev.vra.inventory.application.ReserveInventoryCommand;
import dev.vra.inventory.domain.InventoryKey;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducerOutboxIntegrationTest {
    private static final String PASSWORD = "stage-b-disposable-test-only";
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);

    static {
        POSTGRES.start();
        try {
            bootstrap("validation/poc-01/db/bootstrap.sql");
            bootstrap("validation/poc-03/db/bootstrap-async-roles.sql");
            if (new MigrationRunner().migrate(POSTGRES.getJdbcUrl(), "vra_migrator", PASSWORD) != 3) {
                throw new IllegalStateException("Expected V1, V2, and V3");
            }
        } catch (Exception error) {
            POSTGRES.stop();
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("vra.database.url", POSTGRES::getJdbcUrl);
        registry.add("vra.database.username", () -> "vra_runtime");
        registry.add("vra.database.password", () -> PASSWORD);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Autowired private ReservationApplicationService ordinary;
    @Autowired private IdempotentReservationApplicationService idempotent;

    @Test
    void ordinaryAndIdempotentFirstSuccessCommitExactProducerRows() throws Exception {
        InventoryKey ordinaryKey = seed(10);
        ReservationResult ordinaryResult = ordinary.reserve(new ReserveInventoryCommand(ordinaryKey, 3));
        assertSuccess(ordinaryKey, ordinaryResult.reservationId(), 3);
        assertEquals(0, count("SELECT count(*) FROM vra.inventory_reservation_idempotency "
                + "WHERE reservation_id='" + ordinaryResult.reservationId() + "'"));

        InventoryKey idempotentKey = seed(10);
        IdempotentReserveInventoryCommand command = command(idempotentKey, 4);
        var success = assertInstanceOf(IdempotentReservationResult.Succeeded.class, idempotent.reserve(command));
        assertSuccess(idempotentKey, success.reservationId(), 4);
        assertEquals(1, count("SELECT count(*) FROM vra.inventory_reservation_idempotency WHERE "
                + identity(command) + " AND outcome_status='SUCCEEDED' AND reservation_id='"
                + success.reservationId() + "' AND completed_at IS NOT NULL"));
    }

    @Test
    void eventInsertFailureRollsBackBothPaths() throws Exception {
        rollbackWhenProducerInsertFails("outbox_event", "stage_b_reject_event");
    }

    @Test
    void deliveryInsertFailureAfterEventInsertRollsBackBothPaths() throws Exception {
        rollbackWhenProducerInsertFails("outbox_delivery", "stage_b_reject_delivery");
    }

    @Test
    void sameKeySequentialAndLostResultReplayPreserveEventAndCreatedHistory() throws Exception {
        InventoryKey key = seed(10);
        IdempotentReserveInventoryCommand command = command(key, 2);
        var first = assertInstanceOf(IdempotentReservationResult.Succeeded.class, idempotent.reserve(command));
        String original = eventJson(first.reservationId());
        assertEquals(first, idempotent.reserve(command));
        assertSuccess(key, first.reservationId(), 2);
        assertEquals(original, eventJson(first.reservationId()));

        InventoryKey lostKey = seed(10);
        IdempotentReserveInventoryCommand lostCommand = command(lostKey, 1);
        idempotent.reserve(lostCommand); // The caller loses the result after commit.
        UUID committed = UUID.fromString(value("SELECT reservation_id::text FROM "
                + "vra.inventory_reservation_idempotency WHERE " + identity(lostCommand)));
        String committedEvent = eventJson(committed);
        var replay = assertInstanceOf(IdempotentReservationResult.Succeeded.class,
                idempotent.reserve(lostCommand));
        assertEquals(committed, replay.reservationId());
        assertSuccess(lostKey, committed, 1);
        assertEquals(committedEvent, eventJson(committed));
    }

    @Test
    void concurrentSameKeyCallsConvergeToOneReservationEventAndDelivery() throws Exception {
        InventoryKey key = seed(10);
        IdempotentReserveInventoryCommand command = command(key, 2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<IdempotentReservationResult>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Concurrent start timed out");
                    }
                    return idempotent.reserve(command);
                }));
            }
            start.countDown();
            var first = assertInstanceOf(IdempotentReservationResult.Succeeded.class,
                    results.get(0).get(30, TimeUnit.SECONDS));
            assertEquals(first, results.get(1).get(30, TimeUnit.SECONDS));
            assertSuccess(key, first.reservationId(), 2);
            assertEquals(1, count("SELECT count(*) FROM vra.inventory_reservation_idempotency WHERE "
                    + identity(command) + " AND outcome_status='SUCCEEDED'"));
        }
    }

    @Test
    void conflictingPayloadAndRejectionsNeverProduceAnotherEvent() throws Exception {
        InventoryKey key = seed(10);
        IdempotentReserveInventoryCommand command = command(key, 3);
        var first = assertInstanceOf(IdempotentReservationResult.Succeeded.class, idempotent.reserve(command));
        String original = eventJson(first.reservationId());
        assertEquals(new IdempotentReservationResult.Conflict(
                        IdempotentReservationResult.IdempotencyFailureCode.IDEMPOTENCY_KEY_REUSED),
                idempotent.reserve(new IdempotentReserveInventoryCommand(
                        command.actorScope(), command.idempotencyKey(), key, 2)));
        assertSuccess(key, first.reservationId(), 3);
        assertEquals(original, eventJson(first.reservationId()));

        InventoryKey rejectedKey = seed(1);
        IdempotentReserveInventoryCommand rejected = command(rejectedKey, 2);
        assertEquals(new IdempotentReservationResult.Rejected(ReservationFailureCode.INSUFFICIENT_STOCK),
                idempotent.reserve(rejected));
        assertEquals(1, count("SELECT count(*) FROM vra.inventory_reservation_idempotency WHERE "
                + identity(rejected) + " AND outcome_status='REJECTED'"));
        assertNoProducerRows(rejectedKey);
        assertThrows(ReservationFailureException.class,
                () -> ordinary.reserve(new ReserveInventoryCommand(rejectedKey, 2)));
        assertNoProducerRows(rejectedKey);

        InventoryKey invalidKey = seed(10);
        assertThrows(ReservationFailureException.class,
                () -> ordinary.reserve(new ReserveInventoryCommand(invalidKey, 0)));
        assertThrows(ReservationFailureException.class,
                () -> idempotent.reserve(command(invalidKey, 0)));
        assertNoProducerRows(invalidKey);
    }

    @Test
    void runtimeCanPublishButCannotWriteAsyncTablesOrCallLaterOperations() throws Exception {
        InventoryKey key = seed(10);
        UUID reservationId = ordinary.reserve(new ReserveInventoryCommand(key, 1)).reservationId();
        assertSuccess(key, reservationId, 1); // actual runtime EXECUTE succeeded
        String before = eventJson(reservationId);
        for (String table : List.of("outbox_event", "outbox_delivery", "outbox_delivery_history")) {
            deniedRuntime("INSERT INTO vra." + table + " DEFAULT VALUES");
            deniedRuntime("UPDATE vra." + table + " SET event_id=event_id");
            deniedRuntime("DELETE FROM vra." + table);
        }
        deniedRuntime("SELECT * FROM vra.async_claim_delivery('RESERVATION_PROJECTION','test',1,1000::bigint)");
        deniedRuntime("SELECT vra.async_control_replay(gen_random_uuid(),'CONTROLLED_REPLAY','test')");
        deniedRuntime("SELECT vra.async_control_resume(gen_random_uuid(),'CONTROLLED_RESUME','test')");
        deniedRuntime("SELECT vra.async_control_close(gen_random_uuid(),'CONTROLLED_CLOSE','test')");
        assertSuccess(key, reservationId, 1);
        assertEquals(before, eventJson(reservationId));
    }

    private void rollbackWhenProducerInsertFails(String table, String constraint) throws Exception {
        owner("ALTER TABLE vra." + table + " ADD CONSTRAINT " + constraint + " CHECK (false) NOT VALID");
        try {
            for (boolean useIdempotency : new boolean[] {false, true}) {
                InventoryKey key = seed(10);
                IdempotentReserveInventoryCommand command = command(key, 2);
                RuntimeException failure = assertThrows(RuntimeException.class, () -> {
                    if (useIdempotency) {
                        idempotent.reserve(command);
                    } else {
                        ordinary.reserve(new ReserveInventoryCommand(key, 2));
                    }
                });
                assertTrue(hasSqlState(failure, "23514"), "Expected the injected PostgreSQL CHECK failure");
                assertEquals("10:0:0", value("SELECT on_hand||':'||reserved||':'||version "
                        + "FROM vra.inventory_balance WHERE " + keyWhere(key)));
                assertNoProducerRows(key);
                assertEquals(0, count("SELECT count(*) FROM vra.inventory_reservation_idempotency WHERE "
                        + identity(command)));
            }
        } finally {
            owner("ALTER TABLE vra." + table + " DROP CONSTRAINT " + constraint);
        }
    }

    private static void assertSuccess(InventoryKey key, UUID reservationId, long quantity) throws SQLException {
        assertEquals("10:" + quantity + ":1", value("SELECT on_hand||':'||reserved||':'||version "
                + "FROM vra.inventory_balance WHERE " + keyWhere(key)));
        assertEquals(1, count("SELECT count(*) FROM vra.inventory_reservation WHERE " + keyWhere(key)));
        assertEquals(quantity, count("SELECT quantity FROM vra.inventory_reservation WHERE reservation_id='"
                + reservationId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_event WHERE reservation_id='"
                + reservationId + "'"));
        String eventId = value("SELECT event_id::text FROM vra.outbox_event WHERE reservation_id='"
                + reservationId + "'");
        assertNotEquals(reservationId, UUID.fromString(eventId));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery WHERE event_id='" + eventId + "'"));
        assertEquals("RESERVATION_PROJECTION:READY:1:0:5", value("SELECT target_code||':'||state||':'||"
                + "automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit "
                + "FROM vra.outbox_delivery WHERE event_id='" + eventId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + eventId + "' AND action_code='CREATED' AND from_state IS NULL AND to_state='READY'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + eventId + "'"));
        assertEquals("inventory.reservation.created:1", value("SELECT event_type||':'||schema_version "
                + "FROM vra.outbox_event WHERE event_id='" + eventId + "'"));
        assertEquals("t", value("SELECT (e.reservation_id,e.sku_id,e.owner_id,e.location_id,"
                + "e.stock_status,e.quantity,e.occurred_at)=(r.reservation_id,r.sku_id,r.owner_id,"
                + "r.location_id,r.stock_status,r.quantity,r.created_at) FROM vra.outbox_event e "
                + "JOIN vra.inventory_reservation r USING(reservation_id) WHERE e.event_id='" + eventId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.reservation_projection WHERE reservation_id='"
                + reservationId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='" + eventId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='" + eventId + "'"));
    }

    private static void assertNoProducerRows(InventoryKey key) throws SQLException {
        assertEquals(0, count("SELECT count(*) FROM vra.inventory_reservation WHERE " + keyWhere(key)));
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_event WHERE sku_id='"
                + key.skuId() + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_delivery d JOIN vra.outbox_event e USING(event_id) "
                + "WHERE e.sku_id='" + key.skuId() + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_delivery_history h JOIN vra.outbox_event e "
                + "USING(event_id) WHERE e.sku_id='" + key.skuId() + "'"));
    }

    private static String eventJson(UUID reservationId) throws SQLException {
        return value("SELECT row_to_json(e)::text FROM vra.outbox_event e WHERE reservation_id='"
                + reservationId + "'");
    }

    private static InventoryKey seed(long onHand) throws SQLException {
        InventoryKey key = new InventoryKey(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), StockStatus.AVAILABLE);
        owner("INSERT INTO vra.inventory_balance VALUES ('" + key.skuId() + "','" + key.ownerId()
                + "','" + key.locationId() + "','AVAILABLE'," + onHand + ",0,0)");
        return key;
    }

    private static IdempotentReserveInventoryCommand command(InventoryKey key, long quantity) {
        return new IdempotentReserveInventoryCommand("stage-b-actor", UUID.randomUUID().toString(), key, quantity);
    }

    private static String identity(IdempotentReserveInventoryCommand command) {
        return "actor_scope='" + command.actorScope() + "' AND idempotency_key='"
                + command.idempotencyKey() + "'";
    }

    private static String keyWhere(InventoryKey key) {
        return "sku_id='" + key.skuId() + "' AND owner_id='" + key.ownerId()
                + "' AND location_id='" + key.locationId() + "' AND stock_status='"
                + key.stockStatus().name() + "'";
    }

    private static int count(String sql) throws SQLException {
        return Integer.parseInt(value(sql));
    }

    private static String value(String sql) throws SQLException {
        try (Connection connection = connection("postgres");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), sql);
            String value = result.getString(1);
            assertFalse(result.next(), sql);
            return value;
        }
    }

    private static void owner(String sql) throws SQLException {
        try (Connection connection = connection("postgres"); Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE vra_owner");
            statement.execute(sql);
        }
    }

    private static void deniedRuntime(String sql) throws SQLException {
        try (Connection connection = connection("vra_runtime"); Statement statement = connection.createStatement()) {
            SQLException error = assertThrows(SQLException.class, () -> statement.execute(sql), sql);
            assertEquals("42501", error.getSQLState(), sql);
        }
    }

    private static boolean hasSqlState(Throwable error, String state) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), role, PASSWORD);
    }

    private static void bootstrap(String script) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
            root = root.getParent();
            if (root == null) throw new IllegalStateException("Repository root missing");
        }
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(root.resolve(script)), "/tmp/stage-b-bootstrap.sql");
        var result = POSTGRES.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                "-v", "runtime_password=" + PASSWORD, "-v", "outbox_worker_password=" + PASSWORD,
                "-v", "reconciliation_worker_password=" + PASSWORD,
                "-v", "async_operator_password=" + PASSWORD,
                "-v", "projection_rebuilder_password=" + PASSWORD,
                "-v", "async_observer_password=" + PASSWORD,
                "-f", "/tmp/stage-b-bootstrap.sql");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }
}
