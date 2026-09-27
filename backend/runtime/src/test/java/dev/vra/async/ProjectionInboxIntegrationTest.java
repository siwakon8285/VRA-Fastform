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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import dev.vra.async.adapter.out.delivery.JdbcDeliveryRepository;
import dev.vra.async.adapter.out.projection.JdbcConsumerInbox;
import dev.vra.async.adapter.out.projection.JdbcReservationProjection;
import dev.vra.async.application.delivery.DeliveryPort;
import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.application.delivery.DeliveryService;
import dev.vra.async.application.projection.ReservationProjectionConsumer;
import dev.vra.async.application.projection.ReservationProjectionConsumer.ConsumerInbox;
import dev.vra.async.application.projection.ReservationProjectionConsumer.Outcome;
import dev.vra.async.application.projection.ReservationProjectionConsumer.ReservationProjection;
import dev.vra.async.contract.ReservationCreatedEventCodec;
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
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class ProjectionInboxIntegrationTest {
    private static final String PASSWORD = "stage-d-disposable-test-only";
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static AnnotationConfigApplicationContext context;
    private static DeliveryService delivery;
    private static ReservationProjectionConsumer consumer;
    private static CountDownLatch firstInboxInserted;
    private static CountDownLatch releaseFirstConsumer;

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.start();
        try {
            bootstrap("validation/poc-01/db/bootstrap.sql");
            bootstrap("validation/poc-03/db/bootstrap-async-roles.sql");
            assertEquals(3, new MigrationRunner().migrate(POSTGRES.getJdbcUrl(), "vra_migrator", PASSWORD));
            context = new AnnotationConfigApplicationContext(WorkerConfiguration.class);
            delivery = context.getBean(DeliveryService.class);
            consumer = context.getBean(ReservationProjectionConsumer.class);
        } catch (Exception failure) {
            POSTGRES.stop();
            throw failure;
        }
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
        POSTGRES.stop();
    }

    @BeforeEach
    void clearRows() throws SQLException {
        owner("TRUNCATE vra.consumer_inbox,vra.reservation_projection,vra.outbox_event CASCADE");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class WorkerConfiguration {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "vra_outbox_worker", PASSWORD);
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new JdbcTransactionManager(source);
        }
        @Bean DeliveryPort deliveryPort(DataSource source) {
            return new JdbcDeliveryRepository(JdbcClient.create(source));
        }
        @Bean DeliveryService deliveryService(DeliveryPort port) {
            return new DeliveryService(port);
        }
        @Bean ConsumerInbox consumerInbox(DataSource source) {
            return new JdbcConsumerInbox(JdbcClient.create(source));
        }
        @Bean ReservationProjection reservationProjection(DataSource source) {
            return new JdbcReservationProjection(JdbcClient.create(source));
        }
        @Bean ReservationProjectionConsumer projectionConsumer(ConsumerInbox inbox, ReservationProjection projection) {
            return new ReservationProjectionConsumer(inbox, projection);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CoordinatedInboxConfiguration {
        @Bean
        @Primary
        ConsumerInbox coordinatedInbox(DataSource source) {
            JdbcConsumerInbox delegate = new JdbcConsumerInbox(JdbcClient.create(source));
            return new ConsumerInbox() {
                @Override public boolean register(UUID eventId) {
                    boolean inserted = delegate.register(eventId);
                    if (inserted) {
                        firstInboxInserted.countDown();
                        try {
                            if (!releaseFirstConsumer.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError("Test coordination timed out");
                            }
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(error);
                        }
                    }
                    return inserted;
                }
                @Override public boolean contains(UUID eventId) {
                    return delegate.contains(eventId);
                }
            };
        }
    }

    @Test
    void normalClaimConsumesAtomicallyThenFinalizes() throws Exception {
        Fixture fixture = fixture(3);
        assertEquals(0, projectionCount(fixture)); // committed reservation may lead its projection
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId
                + "' AND target_code='RESERVATION_PROJECTION' AND state='READY'"));
        ClaimedDelivery claim = claim(fixture.eventId);
        ReservationCreatedEventV1 event = readEvent(fixture.eventId);
        assertEquals(Outcome.APPLIED, consumer.consume(event));
        assertEquals("PROCESSING", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'")); // consumer committed before finalize
        assertEquals(1, inboxCount(fixture));
        assertEquals(1, projectionCount(fixture));
        assertProjectionMatchesAuthoritative(fixture);
        assertEquals("t", value("SELECT processed_at BETWEEN d.created_at AND statement_timestamp() "
                + "FROM vra.consumer_inbox i JOIN vra.outbox_delivery d USING(event_id) "
                + "WHERE i.event_id='" + fixture.eventId + "'"));
        assertEquals("t", value("SELECT projected_at BETWEEN d.created_at AND statement_timestamp() "
                + "FROM vra.reservation_projection p JOIN vra.outbox_event e USING(reservation_id) "
                + "JOIN vra.outbox_delivery d USING(event_id) WHERE e.event_id='" + fixture.eventId + "'"));
        assertTrue(delivery.complete(claim));
        assertEquals("SUCCEEDED", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
    }

    @Test
    void sequentialDuplicateUsesDurableInboxWithoutSecondEffect() throws Exception {
        Fixture fixture = fixture(2);
        ReservationCreatedEventV1 event = readEvent(fixture.eventId);
        assertEquals(Outcome.APPLIED, consumer.consume(event));
        String before = projectionRow(fixture);
        String inboxBefore = value("SELECT processed_at::text FROM vra.consumer_inbox WHERE event_id='"
                + fixture.eventId + "'");
        assertEquals(Outcome.ALREADY_APPLIED, consumer.consume(event));
        assertEquals(before, projectionRow(fixture));
        assertEquals(inboxBefore, value("SELECT processed_at::text FROM vra.consumer_inbox WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(1, inboxCount(fixture));
        assertEquals(1, projectionCount(fixture));
    }

    @Test
    void overlappingDuplicatesConvergeThroughPostgresqlUniqueIdentity() throws Exception {
        Fixture fixture = fixture(4);
        ReservationCreatedEventV1 event = readEvent(fixture.eventId);
        firstInboxInserted = new CountDownLatch(1);
        releaseFirstConsumer = new CountDownLatch(1);
        try (AnnotationConfigApplicationContext coordinated = new AnnotationConfigApplicationContext(
                WorkerConfiguration.class, CoordinatedInboxConfiguration.class);
             var pool = Executors.newFixedThreadPool(2)) {
            ReservationProjectionConsumer concurrent = coordinated.getBean(ReservationProjectionConsumer.class);
            var first = pool.submit(() -> concurrent.consume(event));
            assertTrue(firstInboxInserted.await(10, TimeUnit.SECONDS));
            var second = pool.submit(() -> concurrent.consume(event));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            assertEquals(0, inboxCount(fixture)); // first registration remains uncommitted
            releaseFirstConsumer.countDown();
            assertEquals(Outcome.APPLIED, first.get(10, TimeUnit.SECONDS));
            assertEquals(Outcome.ALREADY_APPLIED, second.get(10, TimeUnit.SECONDS));
        } finally {
            releaseFirstConsumer.countDown();
        }
        assertEquals(1, inboxCount(fixture));
        assertEquals(1, projectionCount(fixture));
        assertProjectionMatchesAuthoritative(fixture);
    }

    @Test
    void projectionFailureAfterInboxInsertRollsBothBackAndCanRetry() throws Exception {
        Fixture fixture = fixture(1);
        ClaimedDelivery claim = claim(fixture.eventId);
        owner("ALTER TABLE vra.reservation_projection ADD CONSTRAINT stage_d_reject CHECK (false) NOT VALID");
        try {
            assertThrows(DataAccessException.class, () -> consumer.consume(readEvent(fixture.eventId)));
            assertEquals(0, inboxCount(fixture));
            assertEquals(0, projectionCount(fixture));
            assertEquals("PROCESSING", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                    + fixture.eventId + "'"));
            assertEquals(0, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                    + fixture.eventId + "' AND action_code='SUCCEEDED'"));
        } finally {
            owner("ALTER TABLE vra.reservation_projection DROP CONSTRAINT stage_d_reject");
        }
        assertEquals(Outcome.APPLIED, consumer.consume(readEvent(fixture.eventId)));
        assertTrue(delivery.complete(claim));
        assertEquals(1, inboxCount(fixture));
        assertEquals(1, projectionCount(fixture));
    }

    @Test
    void identicalPreexistingProjectionConvergesWithoutOverwrite() throws Exception {
        Fixture fixture = fixture(2);
        preexistingProjection(fixture, 2);
        String before = projectionRow(fixture);
        assertEquals(Outcome.APPLIED, consumer.consume(readEvent(fixture.eventId)));
        assertEquals(before, projectionRow(fixture));
        assertEquals(1, inboxCount(fixture));
        assertProjectionMatchesAuthoritative(fixture);
    }

    @Test
    void mismatchingPreexistingProjectionRejectsAndRollsBackInbox() throws Exception {
        Fixture fixture = fixture(2);
        preexistingProjection(fixture, 3);
        String before = projectionRow(fixture);
        assertThrows(IllegalStateException.class, () -> consumer.consume(readEvent(fixture.eventId)));
        assertEquals(0, inboxCount(fixture));
        assertEquals(before, projectionRow(fixture));
    }

    @Test
    void committedConsumerBeforeOmittedFinalizeRedeliversWithoutEffect() throws Exception {
        Fixture fixture = fixture(1);
        ClaimedDelivery old = claim(fixture.eventId);
        ReservationCreatedEventV1 event = readEvent(fixture.eventId);
        assertEquals(Outcome.APPLIED, consumer.consume(event));
        String before = projectionRow(fixture);
        assertEquals("PROCESSING", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
        owner("UPDATE vra.outbox_delivery SET claim_until=statement_timestamp()-interval '1 second' "
                + "WHERE event_id='" + fixture.eventId + "'");
        ClaimedDelivery current = claim(fixture.eventId);
        assertTrue(current.reclaimed());
        assertNotEquals(old.claimToken(), current.claimToken());
        assertEquals(Outcome.ALREADY_APPLIED, consumer.consume(event));
        assertEquals(before, projectionRow(fixture));
        assertTrue(delivery.complete(current));
        assertFalse(delivery.complete(old));
        assertEquals("SUCCEEDED", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(1, inboxCount(fixture));
        assertEquals(1, projectionCount(fixture));
    }

    @Test
    void independentEventsCanProjectInReverseCreationOrder() throws Exception {
        List<Fixture> fixtures = List.of(fixture(1), fixture(2), fixture(3));
        List<ClaimedDelivery> claims = delivery.claim(TargetCode.RESERVATION_PROJECTION,
                "worker-d", 3, Duration.ofSeconds(30));
        assertEquals(3, claims.size());
        Set<UUID> claimedIds = Set.of(claims.get(0).eventId(), claims.get(1).eventId(), claims.get(2).eventId());
        assertEquals(Set.of(fixtures.get(0).eventId, fixtures.get(1).eventId, fixtures.get(2).eventId), claimedIds);
        for (int index = fixtures.size() - 1; index >= 0; index--) {
            Fixture fixture = fixtures.get(index);
            assertEquals(Outcome.APPLIED, consumer.consume(readEvent(fixture.eventId)));
            assertProjectionMatchesAuthoritative(fixture);
            ClaimedDelivery claim = claims.stream().filter(c -> c.eventId().equals(fixture.eventId))
                    .findFirst().orElseThrow();
            assertTrue(delivery.complete(claim));
        }
        for (Fixture fixture : fixtures) {
            assertEquals(1, inboxCount(fixture));
            assertEquals(1, projectionCount(fixture));
        }
    }

    @Test
    void unsupportedContractFailsBeforeInboxRegistration() throws Exception {
        Fixture fixture = fixture(1);
        String valid = new ReservationCreatedEventCodec().encode(readEvent(fixture.eventId));
        assertThrows(IllegalArgumentException.class, () -> new ReservationCreatedEventCodec().decode(
                valid.replace("inventory.reservation.created", "inventory.reservation.changed")));
        assertThrows(IllegalArgumentException.class, () -> new ReservationCreatedEventCodec().decode(
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        assertThrows(IllegalArgumentException.class, () -> new ReservationCreatedEventCodec().decode(
                valid.replace("\"quantity\":1", "\"quantity\":0")));
        assertEquals(0, inboxCount(fixture));
        assertEquals(0, projectionCount(fixture));
        assertEquals("READY", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
    }

    @Test
    void workerCanInsertButCannotMaintainProjectionInboxOrBusinessTables() throws Exception {
        Fixture fixture = fixture(2);
        assertEquals(Outcome.APPLIED, consumer.consume(readEvent(fixture.eventId)));
        String projectionBefore = projectionRow(fixture);
        String inboxBefore = value("SELECT row_to_json(i)::text FROM vra.consumer_inbox i WHERE event_id='"
                + fixture.eventId + "'");
        for (String sql : List.of(
                "UPDATE vra.reservation_projection SET quantity=quantity WHERE reservation_id='" + fixture.reservationId + "'",
                "DELETE FROM vra.reservation_projection WHERE reservation_id='" + fixture.reservationId + "'",
                "TRUNCATE vra.reservation_projection",
                "UPDATE vra.consumer_inbox SET processed_at=processed_at WHERE event_id='" + fixture.eventId + "'",
                "DELETE FROM vra.consumer_inbox WHERE event_id='" + fixture.eventId + "'",
                "TRUNCATE vra.consumer_inbox",
                "UPDATE vra.inventory_balance SET reserved=reserved",
                "INSERT INTO vra.inventory_reservation DEFAULT VALUES",
                "UPDATE vra.inventory_reservation_idempotency SET outcome_status='SUCCEEDED'")) {
            try (Connection worker = connection("vra_outbox_worker"); Statement statement = worker.createStatement()) {
                SQLException denied = assertThrows(SQLException.class, () -> statement.execute(sql), sql);
                assertEquals("42501", denied.getSQLState(), sql);
            }
        }
        assertEquals(projectionBefore, projectionRow(fixture));
        assertEquals(inboxBefore, value("SELECT row_to_json(i)::text FROM vra.consumer_inbox i WHERE event_id='"
                + fixture.eventId + "'"));
    }

    private static ClaimedDelivery claim(UUID eventId) {
        List<ClaimedDelivery> claims = delivery.claim(TargetCode.RESERVATION_PROJECTION,
                "worker-d", 1, Duration.ofSeconds(30));
        assertEquals(1, claims.size());
        assertEquals(eventId, claims.getFirst().eventId());
        return claims.getFirst();
    }

    private static Fixture fixture(long quantity) throws SQLException {
        Fixture fixture = new Fixture(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), quantity);
        owner("INSERT INTO vra.inventory_balance VALUES ('" + fixture.skuId + "','" + fixture.ownerId
                + "','" + fixture.locationId + "','AVAILABLE',10," + quantity + ",1)");
        owner("INSERT INTO vra.inventory_reservation VALUES ('" + fixture.reservationId + "','"
                + fixture.skuId + "','" + fixture.ownerId + "','" + fixture.locationId
                + "','AVAILABLE'," + quantity + ",statement_timestamp())");
        try (Connection runtime = connection("vra_runtime"); PreparedStatement statement = runtime.prepareStatement(
                "SELECT vra.async_publish_reservation(?::uuid,?::uuid,'RESERVATION_PROJECTION'::varchar)")) {
            statement.setObject(1, fixture.eventId);
            statement.setObject(2, fixture.reservationId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(fixture.eventId, rows.getObject(1, UUID.class));
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
                ReservationCreatedEventV1 event = new ReservationCreatedEventV1(
                        row.getObject("event_id", UUID.class), row.getString("event_type"),
                        row.getInt("schema_version"), row.getTimestamp("occurred_at").toInstant(),
                        new Payload(row.getObject("reservation_id", UUID.class),
                                row.getObject("sku_id", UUID.class), row.getObject("owner_id", UUID.class),
                                row.getObject("location_id", UUID.class),
                                StockStatus.valueOf(row.getString("stock_status")), row.getLong("quantity")));
                assertFalse(row.next());
                return event;
            }
        }
    }

    private static void preexistingProjection(Fixture fixture, long quantity) throws SQLException {
        owner("INSERT INTO vra.reservation_projection VALUES ('" + fixture.reservationId + "','"
                + fixture.skuId + "','" + fixture.ownerId + "','" + fixture.locationId
                + "','AVAILABLE'," + quantity + ",statement_timestamp()-interval '1 day')");
    }

    private static void assertProjectionMatchesAuthoritative(Fixture fixture) throws SQLException {
        assertEquals("t", value("SELECT (p.reservation_id,p.sku_id,p.owner_id,p.location_id,"
                + "p.stock_status,p.quantity)=(r.reservation_id,r.sku_id,r.owner_id,r.location_id,"
                + "r.stock_status,r.quantity) FROM vra.reservation_projection p JOIN vra.inventory_reservation r "
                + "USING(reservation_id) WHERE p.reservation_id='" + fixture.reservationId + "'"));
    }

    private static String projectionRow(Fixture fixture) throws SQLException {
        return value("SELECT row_to_json(p)::text FROM vra.reservation_projection p WHERE reservation_id='"
                + fixture.reservationId + "'");
    }

    private static int inboxCount(Fixture fixture) throws SQLException {
        return count("SELECT count(*) FROM vra.consumer_inbox WHERE consumer_identity='reservation_projection_v1' "
                + "AND event_id='" + fixture.eventId + "'");
    }

    private static int projectionCount(Fixture fixture) throws SQLException {
        return count("SELECT count(*) FROM vra.reservation_projection WHERE reservation_id='"
                + fixture.reservationId + "'");
    }

    private static int count(String sql) throws SQLException {
        return Integer.parseInt(value(sql));
    }

    private static String value(String sql) throws SQLException {
        try (Connection admin = connection("postgres"); Statement statement = admin.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            String value = rows.getString(1);
            assertFalse(rows.next(), sql);
            return value;
        }
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

    private static void bootstrap(String script) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
            root = root.getParent();
            if (root == null) throw new IllegalStateException("Repository root missing");
        }
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(root.resolve(script)), "/tmp/stage-d-bootstrap.sql");
        var result = POSTGRES.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                "-v", "runtime_password=" + PASSWORD, "-v", "outbox_worker_password=" + PASSWORD,
                "-v", "reconciliation_worker_password=" + PASSWORD,
                "-v", "async_operator_password=" + PASSWORD,
                "-v", "projection_rebuilder_password=" + PASSWORD,
                "-v", "async_observer_password=" + PASSWORD,
                "-f", "/tmp/stage-d-bootstrap.sql");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }

    private record Fixture(UUID reservationId, UUID eventId, UUID skuId, UUID ownerId,
                           UUID locationId, long quantity) {}
}
