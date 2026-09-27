package dev.vra.async;

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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import dev.vra.async.adapter.out.delivery.JdbcDeliveryRepository;
import dev.vra.async.adapter.out.projection.JdbcConsumerInbox;
import dev.vra.async.adapter.out.projection.JdbcProjectionRebuilder;
import dev.vra.async.adapter.out.projection.JdbcReservationProjection;
import dev.vra.async.application.delivery.DeliveryPort;
import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.application.delivery.DeliveryService;
import dev.vra.async.application.projection.ProjectionRebuildPort;
import dev.vra.async.application.projection.ProjectionRebuildPort.RebuildResult;
import dev.vra.async.application.projection.ReservationProjectionConsumer;
import dev.vra.async.application.projection.ReservationProjectionConsumer.ConsumerInbox;
import dev.vra.async.application.projection.ReservationProjectionConsumer.Outcome;
import dev.vra.async.application.projection.ReservationProjectionConsumer.ReservationProjection;
import dev.vra.async.contract.ReservationCreatedEventV1;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class ProjectionRebuildIntegrationTest {
    private static final String PASSWORD = "stage-h-disposable-test-only";
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static AnnotationConfigApplicationContext workerContext;
    private static AnnotationConfigApplicationContext rebuilderContext;
    private static DeliveryService delivery;
    private static ReservationProjectionConsumer consumer;
    private static ProjectionRebuildPort rebuilder;

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.start();
        try {
            bootstrap("validation/poc-01/db/bootstrap.sql");
            bootstrap("validation/poc-03/db/bootstrap-async-roles.sql");
            assertEquals(3, new MigrationRunner().migrate(POSTGRES.getJdbcUrl(), "vra_migrator", PASSWORD));
            workerContext = new AnnotationConfigApplicationContext(WorkerConfiguration.class);
            rebuilderContext = new AnnotationConfigApplicationContext(RebuilderConfiguration.class);
            delivery = workerContext.getBean(DeliveryService.class);
            consumer = workerContext.getBean(ReservationProjectionConsumer.class);
            rebuilder = rebuilderContext.getBean(ProjectionRebuildPort.class);
        } catch (Exception error) {
            stop();
            throw error;
        }
    }

    @AfterAll
    static void stop() {
        if (rebuilderContext != null) rebuilderContext.close();
        if (workerContext != null) workerContext.close();
        POSTGRES.stop();
    }

    @BeforeEach
    void clear() throws SQLException {
        owner("TRUNCATE vra.consumer_inbox,vra.reservation_projection,vra.outbox_event,"
                + "vra.inventory_reservation,vra.inventory_balance CASCADE");
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
        @Bean ConsumerInbox consumerInbox(DataSource source) {
            return new JdbcConsumerInbox(JdbcClient.create(source));
        }
        @Bean ReservationProjection projection(DataSource source) {
            return new JdbcReservationProjection(JdbcClient.create(source));
        }
        @Bean ReservationProjectionConsumer consumer(ConsumerInbox inbox, ReservationProjection projection) {
            return new ReservationProjectionConsumer(inbox, projection);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class RebuilderConfiguration {
        @Bean DataSource dataSource() { return source("vra_projection_rebuilder"); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new JdbcTransactionManager(source);
        }
        @Bean JdbcClient jdbcClient(DataSource source) { return JdbcClient.create(source); }
        @Bean ProjectionRebuildPort rebuilder(JdbcClient jdbc) { return new JdbcProjectionRebuilder(jdbc); }
    }

    @Test
    void authoritativeRepairIgnoresStaleInboxAndOutboxHistoryThenRedeliveryConverges() throws Exception {
        Fixture missing = fixture(true);
        Fixture damaged = fixture(true);
        Fixture firstProcessing = fixture(true);
        Fixture withoutEvent = fixture(false);
        assertEquals(Outcome.APPLIED, consumer.consume(readEvent(missing.eventId)));
        assertEquals(Outcome.APPLIED, consumer.consume(readEvent(damaged.eventId)));
        owner("DELETE FROM vra.reservation_projection WHERE reservation_id='" + missing.reservationId + "'");
        owner("UPDATE vra.reservation_projection SET quantity=quantity+7 WHERE reservation_id='"
                + damaged.reservationId + "'");
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_event WHERE reservation_id='"
                + withoutEvent.reservationId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_delivery_history h JOIN vra.outbox_event e "
                + "USING(event_id) WHERE e.reservation_id='" + withoutEvent.reservationId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='"
                + firstProcessing.eventId + "'"));
        String before = unrelatedSnapshot();

        assertEquals(new RebuildResult(3, 1), rebuilder.rebuild());
        assertEquals(before, unrelatedSnapshot());
        assertSixFieldSetEquality(4);
        assertEquals(1, count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='"
                + missing.eventId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='"
                + damaged.eventId + "'"));
        assertEquals(Outcome.ALREADY_APPLIED, consumer.consume(readEvent(missing.eventId)));
        assertEquals(Outcome.ALREADY_APPLIED, consumer.consume(readEvent(damaged.eventId)));
        assertEquals(Outcome.APPLIED, consumer.consume(readEvent(firstProcessing.eventId)));
        assertSixFieldSetEquality(4);

        String beforeRepeat = unrelatedSnapshot();
        assertEquals(new RebuildResult(0, 4), rebuilder.rebuild());
        assertEquals(beforeRepeat, unrelatedSnapshot());
        assertSixFieldSetEquality(4);
    }

    @Test
    void reservationCommittedAfterRebuildConvergesThroughDurableScenarioADelivery() throws Exception {
        assertEquals(new RebuildResult(0, 0), rebuilder.rebuild());
        Fixture later = fixture(true);
        assertEquals(0, count("SELECT count(*) FROM vra.reservation_projection WHERE reservation_id='"
                + later.reservationId + "'"));
        ClaimedDelivery claim = only(delivery.claim(TargetCode.RESERVATION_PROJECTION,
                "worker-h", 1, Duration.ofSeconds(30)));
        assertEquals(later.eventId, claim.eventId());
        assertEquals(Outcome.APPLIED, consumer.consume(readEvent(later.eventId)));
        assertTrue(delivery.complete(claim));
        assertEquals("SUCCEEDED", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + later.eventId + "'"));
        assertSixFieldSetEquality(1);
    }

    @Test
    void rebuildTableLockSerializesConsumerProjectionWriteUntilCommit() throws Exception {
        Fixture fixture = fixture(true);
        CountDownLatch functionReturned = new CountDownLatch(1);
        CountDownLatch permitCommit = new CountDownLatch(1);
        AtomicInteger rebuilderPid = new AtomicInteger();
        try (var threads = Executors.newFixedThreadPool(2)) {
            var rebuilding = threads.submit(() -> new TransactionTemplate(
                    rebuilderContext.getBean(PlatformTransactionManager.class)).execute(status -> {
                RebuildResult result = rebuilder.rebuild();
                rebuilderPid.set(rebuilderContext.getBean(JdbcClient.class)
                        .sql("SELECT pg_backend_pid()").query(Integer.class).single());
                functionReturned.countDown();
                try {
                    if (!permitCommit.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Rebuild commit barrier timed out");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                return result;
            }));
            try {
                assertTrue(functionReturned.await(10, TimeUnit.SECONDS));
                assertEquals(1, count("SELECT count(*) FROM pg_locks WHERE pid=" + rebuilderPid.get()
                        + " AND relation='vra.reservation_projection'::regclass "
                        + "AND mode='ShareRowExclusiveLock' AND granted"));
                var consuming = threads.submit(() -> consumer.consume(readEvent(fixture.eventId)));
                awaitBlockedConsumer(rebuilderPid.get());
                assertFalse(consuming.isDone());
                assertEquals(0, count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='"
                        + fixture.eventId + "'"));
                permitCommit.countDown();
                assertEquals(new RebuildResult(1, 0), rebuilding.get(10, TimeUnit.SECONDS));
                assertEquals(Outcome.APPLIED, consuming.get(10, TimeUnit.SECONDS));
            } finally {
                permitCommit.countDown();
            }
        }
        assertSixFieldSetEquality(1);
        assertEquals(1, count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='"
                + fixture.eventId + "'"));
    }

    @Test
    void actualRebuilderAndWorkerCredentialsEnforceOnlyTheirCapabilities() throws Exception {
        Fixture fixture = fixture(true);
        assertEquals(1, countAs("vra_projection_rebuilder", "SELECT count(*) FROM vra.inventory_reservation"));
        assertEquals(0, countAs("vra_projection_rebuilder", "SELECT count(*) FROM vra.reservation_projection"));
        assertEquals(new RebuildResult(1, 0), rebuilder.rebuild());
        for (String sql : List.of(
                "INSERT INTO vra.reservation_projection SELECT * FROM vra.reservation_projection WHERE false",
                "UPDATE vra.reservation_projection SET quantity=quantity WHERE reservation_id='" + fixture.reservationId + "'",
                "DELETE FROM vra.reservation_projection WHERE reservation_id='" + fixture.reservationId + "'",
                "TRUNCATE vra.reservation_projection",
                "UPDATE vra.inventory_reservation SET quantity=quantity WHERE reservation_id='" + fixture.reservationId + "'",
                "INSERT INTO vra.inventory_reservation DEFAULT VALUES",
                "UPDATE vra.inventory_balance SET reserved=reserved",
                "INSERT INTO vra.consumer_inbox VALUES ('x','" + fixture.eventId + "',statement_timestamp())",
                "UPDATE vra.consumer_inbox SET processed_at=processed_at",
                "DELETE FROM vra.consumer_inbox",
                "TRUNCATE vra.consumer_inbox",
                "SELECT * FROM vra.async_claim_delivery('RESERVATION_PROJECTION','x',1,1000::bigint)",
                "SELECT * FROM vra.async_claim_reconciliation('x',1,1000::bigint)",
                "SELECT vra.async_control_replay('" + fixture.eventId + "','CONTROLLED_REPLAY','x')",
                "SELECT vra.async_control_resume('" + fixture.eventId + "','CONTROLLED_RESUME','x')",
                "SELECT vra.async_control_close('" + fixture.eventId + "','CONTROLLED_CLOSE','x')",
                "SET ROLE vra_owner", "SET ROLE vra_async_executor")) {
            denied("vra_projection_rebuilder", sql);
        }
        for (String sql : List.of(
                "SELECT * FROM vra.async_rebuild_reservation_projection()",
                "UPDATE vra.reservation_projection SET quantity=quantity WHERE reservation_id='" + fixture.reservationId + "'",
                "DELETE FROM vra.reservation_projection WHERE reservation_id='" + fixture.reservationId + "'",
                "TRUNCATE vra.reservation_projection")) {
            denied("vra_outbox_worker", sql);
        }
        assertSixFieldSetEquality(1);
    }

    private static void awaitBlockedConsumer(int blockerPid) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (count("SELECT count(*) FROM pg_stat_activity a WHERE a.usename='vra_outbox_worker' "
                    + "AND a.wait_event_type='Lock' AND a.query LIKE 'INSERT INTO vra.reservation_projection%' "
                    + "AND " + blockerPid + "=ANY(pg_blocking_pids(a.pid))") > 0) return;
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        fail("No PostgreSQL evidence of consumer blocked behind rebuild lock");
    }

    private static void assertSixFieldSetEquality(int expectedCount) throws SQLException {
        assertEquals(expectedCount, count("SELECT count(*) FROM vra.inventory_reservation"));
        assertEquals(expectedCount, count("SELECT count(*) FROM vra.reservation_projection"));
        String source = "SELECT reservation_id,sku_id,owner_id,location_id,stock_status,quantity "
                + "FROM vra.inventory_reservation";
        String derived = "SELECT reservation_id,sku_id,owner_id,location_id,stock_status,quantity "
                + "FROM vra.reservation_projection";
        assertEquals(0, count("SELECT count(*) FROM (" + source + " EXCEPT " + derived + ") x"));
        assertEquals(0, count("SELECT count(*) FROM (" + derived + " EXCEPT " + source + ") x"));
    }

    private static String unrelatedSnapshot() throws SQLException {
        StringBuilder result = new StringBuilder();
        for (String table : List.of("inventory_balance", "inventory_reservation", "consumer_inbox",
                "outbox_event", "outbox_delivery", "outbox_delivery_history", "reconciliation_case",
                "reconciliation_history")) {
            result.append('|').append(value("SELECT coalesce(jsonb_agg(to_jsonb(t) "
                    + "ORDER BY to_jsonb(t)::text)::text,'[]') FROM vra." + table + " t"));
        }
        return result.toString();
    }

    private record Fixture(UUID reservationId, UUID eventId, UUID skuId, UUID ownerId,
                           UUID locationId, long quantity) {}

    private static Fixture fixture(boolean publish) throws SQLException {
        Fixture fixture = new Fixture(UUID.randomUUID(), publish ? UUID.randomUUID() : null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2);
        owner("INSERT INTO vra.inventory_balance VALUES ('" + fixture.skuId + "','" + fixture.ownerId
                + "','" + fixture.locationId + "','AVAILABLE',10,2,1)");
        owner("INSERT INTO vra.inventory_reservation VALUES ('" + fixture.reservationId + "','"
                + fixture.skuId + "','" + fixture.ownerId + "','" + fixture.locationId
                + "','AVAILABLE',2,statement_timestamp())");
        if (publish) {
            try (Connection runtime = connection("vra_runtime"); PreparedStatement statement = runtime.prepareStatement(
                    "SELECT vra.async_publish_reservation(?::uuid,?::uuid,'RESERVATION_PROJECTION'::varchar)")) {
                statement.setObject(1, fixture.eventId);
                statement.setObject(2, fixture.reservationId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(fixture.eventId, rows.getObject(1, UUID.class));
                }
            }
        }
        return fixture;
    }

    private static ReservationCreatedEventV1 readEvent(UUID eventId) throws SQLException {
        try (Connection worker = connection("vra_outbox_worker"); PreparedStatement statement = worker.prepareStatement(
                "SELECT event_id,event_type,schema_version,occurred_at,reservation_id,sku_id,owner_id,"
                        + "location_id,stock_status,quantity FROM vra.outbox_event WHERE event_id=?")) {
            statement.setObject(1, eventId);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                return new ReservationCreatedEventV1(row.getObject("event_id", UUID.class),
                        row.getString("event_type"), row.getInt("schema_version"),
                        row.getTimestamp("occurred_at").toInstant(), new Payload(
                        row.getObject("reservation_id", UUID.class), row.getObject("sku_id", UUID.class),
                        row.getObject("owner_id", UUID.class), row.getObject("location_id", UUID.class),
                        StockStatus.valueOf(row.getString("stock_status")), row.getLong("quantity")));
            }
        }
    }

    private static ClaimedDelivery only(List<ClaimedDelivery> claims) {
        assertEquals(1, claims.size());
        return claims.getFirst();
    }

    private static DataSource source(String role) {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), role, PASSWORD);
    }

    private static void owner(String sql) throws SQLException {
        try (Connection admin = connection("postgres"); Statement statement = admin.createStatement()) {
            statement.execute("SET ROLE vra_owner");
            statement.execute(sql);
        }
    }

    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), role, PASSWORD);
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

    private static int countAs(String role, String sql) throws SQLException {
        try (Connection db = connection(role); Statement statement = db.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static void denied(String role, String sql) throws SQLException {
        try (Connection db = connection(role); Statement statement = db.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute(sql), sql);
            assertEquals("42501", failure.getSQLState(), sql);
        }
    }

    private static void bootstrap(String script) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
            root = root.getParent();
            if (root == null) throw new IllegalStateException("Repository root missing");
        }
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(root.resolve(script)), "/tmp/stage-h-bootstrap.sql");
        var result = POSTGRES.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                "-v", "runtime_password=" + PASSWORD, "-v", "outbox_worker_password=" + PASSWORD,
                "-v", "reconciliation_worker_password=" + PASSWORD,
                "-v", "async_operator_password=" + PASSWORD,
                "-v", "projection_rebuilder_password=" + PASSWORD,
                "-v", "async_observer_password=" + PASSWORD,
                "-f", "/tmp/stage-h-bootstrap.sql");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }
}
