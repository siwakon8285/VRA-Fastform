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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import dev.vra.async.adapter.out.delivery.JdbcDeliveryRepository;
import dev.vra.async.application.delivery.DeliveryPort;
import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.application.delivery.DeliveryService;
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
class DeliveryClaimIntegrationTest {
    private static final String PASSWORD = "stage-c-disposable-test-only";
    private static final PostgreSQLContainer POSTGRES = new dev.vra.poc04.external.RunOwnedPostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static AnnotationConfigApplicationContext context;
    private static DeliveryService delivery;

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.start();
        try {
            bootstrap("validation/poc-01/db/bootstrap.sql");
            bootstrap("validation/poc-03/db/bootstrap-async-roles.sql");
            bootstrap("validation/poc-04/db/bootstrap-security-roles.sql");
            assertEquals(4, new MigrationRunner().migrate(POSTGRES.getJdbcUrl(), "vra_migrator", PASSWORD));
            context = new AnnotationConfigApplicationContext(WorkerConfiguration.class);
            delivery = context.getBean(DeliveryService.class);
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
    void clearAsyncFixtureRows() throws SQLException {
        owner("TRUNCATE vra.outbox_event CASCADE");
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
    }

    @Test
    void readyClaimCommitsAndRolledBackClaimLeavesNoTrace() throws Exception {
        UUID event = fixture(TargetCode.RESERVATION_PROJECTION);
        ClaimedDelivery claim = only(delivery.claim(TargetCode.RESERVATION_PROJECTION, "worker-a", 1,
                Duration.ofSeconds(30)));
        assertEquals(event, claim.eventId());
        assertEquals(1, claim.deliveryAttemptCount());
        assertEquals(1, claim.cycleClaimCount());
        assertEquals(5, claim.cycleClaimLimit());
        assertFalse(claim.reclaimed());
        assertEquals("PROCESSING:1:1:5:false", value("SELECT state||':'||delivery_attempt_count||':'||"
                + "cycle_claim_count||':'||cycle_claim_limit||':'||claim_relinquished "
                + "FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
        assertEquals("t", value("SELECT claim_until > statement_timestamp() FROM vra.outbox_delivery "
                + "WHERE event_id='" + event + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + event + "' AND action_code='CLAIM' AND claim_token='" + claim.claimToken() + "'"));

        UUID rollbackEvent = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        try (Connection worker = connection("vra_outbox_worker")) {
            worker.setAutoCommit(false);
            try (PreparedStatement statement = worker.prepareStatement(
                    "SELECT event_id FROM vra.async_claim_delivery('VALIDATION_EXTERNAL_EFFECT','rollback',1,30000::bigint)")) {
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(rollbackEvent, rows.getObject(1, UUID.class));
                }
            }
            worker.rollback();
        }
        assertEquals("READY:0:0", value("SELECT state||':'||delivery_attempt_count||':'||cycle_claim_count "
                + "FROM vra.outbox_delivery WHERE event_id='" + rollbackEvent + "'"));
        assertNull(value("SELECT claim_token::text FROM vra.outbox_delivery WHERE event_id='"
                + rollbackEvent + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + rollbackEvent + "' AND action_code IN ('CLAIM','RECLAIM')"));
        ClaimedDelivery afterRollback = only(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT,
                "worker-b", 1, Duration.ofSeconds(30)));
        assertEquals(rollbackEvent, afterRollback.eventId());
        assertEquals(1, afterRollback.cycleClaimCount());
        assertFalse(afterRollback.reclaimed());
    }

    @Test
    void retryWaitBecomesClaimableOnlyAtDatabaseEligibilityTime() throws Exception {
        UUID event = fixture(TargetCode.RESERVATION_PROJECTION);
        ClaimedDelivery first = claim(TargetCode.RESERVATION_PROJECTION);
        assertEquals(event, first.eventId());
        assertEquals("RETRY_WAIT", delivery.retryTransient(first, Duration.ofSeconds(30)));
        assertEquals("t", value("SELECT next_eligible_at > statement_timestamp() FROM vra.outbox_delivery "
                + "WHERE event_id='" + event + "'"));
        assertTrue(delivery.claim(TargetCode.RESERVATION_PROJECTION, "too-early", 1,
                Duration.ofSeconds(30)).isEmpty());
        owner("UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-interval '1 second' "
                + "WHERE event_id='" + event + "'");
        ClaimedDelivery due = claim(TargetCode.RESERVATION_PROJECTION);
        assertEquals(event, due.eventId());
        assertFalse(due.reclaimed());
        assertEquals(2, due.cycleClaimCount());
        assertEquals(5, due.cycleClaimLimit());
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + event + "' AND action_code='CLAIM' AND from_state='RETRY_WAIT' AND cycle_claim_count=2"));
    }

    @Test
    void overlappingWorkerTransactionsSkipLockedAndNeverDuplicate() throws Exception {
        UUID first = fixture(TargetCode.RESERVATION_PROJECTION);
        UUID second = fixture(TargetCode.RESERVATION_PROJECTION);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondCommitted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var workerA = pool.submit(() -> {
                try (Connection a = connection("vra_outbox_worker")) {
                    a.setAutoCommit(false);
                    int pid = Integer.parseInt(scalar(a, "SELECT pg_backend_pid()"));
                    UUID event = UUID.fromString(scalar(a, "SELECT event_id::text FROM "
                            + "vra.async_claim_delivery('RESERVATION_PROJECTION','worker-a',1,30000::bigint)"));
                    firstLocked.countDown();
                    assertTrue(secondCommitted.await(10, TimeUnit.SECONDS));
                    a.commit();
                    return new TransactionClaim(pid, event);
                }
            });
            var workerB = pool.submit(() -> {
                assertTrue(firstLocked.await(10, TimeUnit.SECONDS));
                try (Connection b = connection("vra_outbox_worker")) {
                    b.setAutoCommit(false);
                    int pid = Integer.parseInt(scalar(b, "SELECT pg_backend_pid()"));
                    // A still owns an uncommitted row lock while B commits other work.
                    UUID event = UUID.fromString(scalar(b, "SELECT event_id::text FROM "
                            + "vra.async_claim_delivery('RESERVATION_PROJECTION','worker-b',1,30000::bigint)"));
                    b.commit();
                    secondCommitted.countDown();
                    return new TransactionClaim(pid, event);
                }
            });
            TransactionClaim a = workerA.get(20, TimeUnit.SECONDS);
            TransactionClaim b = workerB.get(20, TimeUnit.SECONDS);
            assertNotEquals(a.pid(), b.pid());
            assertEquals(Set.of(first, second), Set.of(a.event(), b.event()));
        }
        for (UUID event : List.of(first, second)) {
            assertEquals("PROCESSING:1:1:5", value("SELECT state||':'||delivery_attempt_count||':'||"
                    + "cycle_claim_count||':'||cycle_claim_limit FROM vra.outbox_delivery WHERE event_id='"
                    + event + "'"));
            assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                    + event + "' AND action_code='CLAIM'"));
        }
    }

    private record TransactionClaim(int pid, UUID event) {}

    @Test
    void expiryDoesNotRevokeButCommittedReclaimFencesEveryOldOperation() throws Exception {
        UUID canStillComplete = fixture(TargetCode.RESERVATION_PROJECTION);
        ClaimedDelivery c1 = claim(TargetCode.RESERVATION_PROJECTION);
        assertEquals(canStillComplete, c1.eventId());
        expire(canStillComplete);
        assertEquals("t", value("SELECT claim_until < statement_timestamp() FROM vra.outbox_delivery "
                + "WHERE event_id='" + canStillComplete + "'"));
        assertTrue(delivery.complete(c1)); // Expiry only made this eligible for reclaim.

        UUID event = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        ClaimedDelivery old = claim(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        assertEquals(event, old.eventId());
        assertTrue(delivery.claim(TargetCode.VALIDATION_EXTERNAL_EFFECT, "too-early", 1,
                Duration.ofSeconds(30)).isEmpty());
        expire(event);
        ClaimedDelivery current = claim(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        assertTrue(current.reclaimed());
        assertTrue(current.requiresReconciliationBeforeExecute());
        assertNotEquals(old.claimToken(), current.claimToken());
        assertEquals(2, current.cycleClaimCount());
        assertEquals(2, current.deliveryAttemptCount());
        String currentState = value("SELECT state||':'||claim_token||':'||claim_relinquished "
                + "FROM vra.outbox_delivery WHERE event_id='" + event + "'");
        assertEquals("PROCESSING:" + current.claimToken() + ":false", currentState);
        int history = count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event + "'");
        assertEquals(0, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='" + event + "'"));

        assertThrows(IllegalStateException.class, () -> delivery.complete(current));
        assertThrows(IllegalStateException.class,
                () -> delivery.retryTransient(current, Duration.ofMillis(1)));
        assertThrows(IllegalStateException.class, () -> delivery.failNonRetryable(current));
        assertThrows(IllegalStateException.class, () -> delivery.handoffUnknown(current));
        assertEquals(currentState, value("SELECT state||':'||claim_token||':'||claim_relinquished "
                + "FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
        assertEquals(history, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='" + event + "'"));

        assertFalse(delivery.complete(old));
        assertNull(delivery.retryTransient(old, Duration.ofMillis(1)));
        assertFalse(delivery.failNonRetryable(old));
        assertNull(delivery.handoffUnknown(old));
        assertFalse(delivery.relinquish(old));
        assertEquals(history, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event + "'"));
        assertEquals(current.claimToken().toString(), value("SELECT claim_token::text FROM vra.outbox_delivery "
                + "WHERE event_id='" + event + "'"));
        UUID caseId = delivery.handoffReclaimedExternal(current);
        assertNotNull(caseId);
        assertEquals("RECONCILIATION_REQUIRED:UNKNOWN_OUTCOME:RECLAIM_POSSIBLE_EXTERNAL_SEND",
                value("SELECT state||':'||failure_class||':'||reason_code FROM vra.outbox_delivery "
                        + "WHERE event_id='" + event + "'"));
        assertEquals("PENDING:UNKNOWN:4", value("SELECT state||':'||external_knowledge||':'||cycle_claim_limit "
                + "FROM vra.reconciliation_case WHERE case_id='" + caseId + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='" + event + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + event + "' AND action_code='UNKNOWN_HANDOFF'"));
        assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"
                + caseId + "' AND action_code='CREATED'"));
    }

    @Test
    void relinquishmentImmediatelyFencesAndRollbackKeepsOldLease() throws Exception {
        UUID event = fixture(TargetCode.RESERVATION_PROJECTION);
        ClaimedDelivery old = claim(TargetCode.RESERVATION_PROJECTION);
        assertEquals(event, old.eventId());
        String before = value("SELECT claim_until::text FROM vra.outbox_delivery WHERE event_id='" + event + "'");
        try (Connection worker = connection("vra_outbox_worker")) {
            worker.setAutoCommit(false);
            assertEquals("t", scalar(worker, "SELECT vra.async_relinquish_delivery('" + event + "','"
                    + old.claimToken() + "')"));
            worker.rollback();
        }
        assertEquals(before, value("SELECT claim_until::text FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
        assertEquals("f", value("SELECT claim_relinquished FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + event + "' AND action_code='RELINQUISH'"));
        assertTrue(delivery.relinquish(old));
        assertEquals("PROCESSING:true:" + old.claimToken(), value("SELECT state||':'||claim_relinquished||':'||"
                + "claim_token FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
        assertEquals("t", value("SELECT claim_until <= statement_timestamp() FROM vra.outbox_delivery "
                + "WHERE event_id='" + event + "'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + event + "' AND action_code='RELINQUISH' AND claim_token='" + old.claimToken() + "'"));
        int history = count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event + "'");
        assertFalse(delivery.complete(old));
        assertNull(delivery.retryTransient(old, Duration.ofMillis(1)));
        assertFalse(delivery.failNonRetryable(old));
        assertNull(delivery.handoffUnknown(old));
        assertFalse(delivery.relinquish(old));
        assertEquals(history, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event + "'"));
        ClaimedDelivery current = claim(TargetCode.RESERVATION_PROJECTION);
        assertTrue(current.reclaimed());
        assertNotEquals(old.claimToken(), current.claimToken());
        assertTrue(delivery.complete(current));
    }

    @Test
    void fiveDispatchableClaimsThenOneRecoveryOnlyForEachScenario() throws Exception {
        for (TargetCode target : TargetCode.values()) {
            UUID event = fixture(target);
            Set<UUID> tokens = new HashSet<>();
            for (int count = 1; count <= 5; count++) {
                ClaimedDelivery claim = claim(target);
                assertEquals(event, claim.eventId());
                assertEquals(count, claim.cycleClaimCount());
                assertEquals(count, claim.deliveryAttemptCount());
                assertEquals(5, claim.cycleClaimLimit());
                assertEquals(count > 1, claim.reclaimed());
                assertTrue(tokens.add(claim.claimToken()));
                assertTrue(delivery.relinquish(claim));
            }
            // A sixth DB claim performs recovery but returns no dispatchable work.
            assertTrue(delivery.claim(target, "worker-recovery", 1, Duration.ofSeconds(30)).isEmpty());
            assertEquals("6:6:5", value("SELECT delivery_attempt_count||':'||cycle_claim_count||':'||"
                    + "cycle_claim_limit FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
            assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                    + event + "' AND action_code='RECLAIM' AND cycle_claim_count=6"));
            if (target == TargetCode.RESERVATION_PROJECTION) {
                assertEquals("FAILED:OPERATOR_REQUIRED:RETRY_EXHAUSTED", value("SELECT state||':'||"
                        + "failure_class||':'||reason_code FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
                assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                        + event + "' AND action_code='RECOVERY_ONLY_FAILED' AND cycle_claim_count=6"));
            } else {
                assertEquals("RECONCILIATION_REQUIRED:UNKNOWN_OUTCOME:RECLAIM_POSSIBLE_EXTERNAL_SEND",
                        value("SELECT state||':'||failure_class||':'||reason_code FROM vra.outbox_delivery "
                                + "WHERE event_id='" + event + "'"));
                assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                        + event + "' AND action_code='RECOVERY_ONLY_UNKNOWN_HANDOFF' AND cycle_claim_count=6"));
                assertEquals("PENDING:UNKNOWN:1:0:4", value("SELECT state||':'||external_knowledge||':'||"
                        + "automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit "
                        + "FROM vra.reconciliation_case WHERE event_id='" + event + "'"));
                assertEquals(1, count("SELECT count(*) FROM vra.reconciliation_history h JOIN "
                        + "vra.reconciliation_case c USING(case_id) WHERE c.event_id='" + event
                        + "' AND h.action_code='CREATED'"));
            }
            int history = count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event + "'");
            assertTrue(delivery.claim(target, "worker-repeat", 1, Duration.ofSeconds(30)).isEmpty());
            assertEquals(history, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event + "'"));
        }
    }

    @Test
    void workerHasOnlyGuardedDeliveryCapabilities() throws Exception {
        UUID event = fixture(TargetCode.RESERVATION_PROJECTION);
        ClaimedDelivery claim = claim(TargetCode.RESERVATION_PROJECTION); // positive actual EXECUTE
        assertEquals(event, claim.eventId());
        String before = value("SELECT row_to_json(d)::text FROM vra.outbox_delivery d WHERE event_id='" + event + "'");
        for (String sql : List.of(
                "UPDATE vra.outbox_delivery SET state='FAILED' WHERE event_id='" + event + "'",
                "UPDATE vra.outbox_delivery SET cycle_claim_limit=16 WHERE event_id='" + event + "'",
                "INSERT INTO vra.outbox_delivery_history DEFAULT VALUES",
                "UPDATE vra.outbox_delivery_history SET action_code='FAILED' WHERE event_id='" + event + "'",
                "DELETE FROM vra.outbox_delivery_history WHERE event_id='" + event + "'",
                "UPDATE vra.inventory_balance SET reserved=reserved",
                "INSERT INTO vra.inventory_reservation DEFAULT VALUES",
                "UPDATE vra.inventory_reservation_idempotency SET outcome_status='SUCCEEDED'",
                "SELECT vra.async_control_replay('" + event + "','CONTROLLED_REPLAY','worker')",
                "SELECT vra.async_control_resume(gen_random_uuid(),'CONTROLLED_RESUME','worker')",
                "SELECT vra.async_control_close('" + event + "','CONTROLLED_CLOSE','worker')",
                "SET ROLE vra_owner", "SET ROLE vra_async_executor")) {
            try (Connection worker = connection("vra_outbox_worker"); Statement statement = worker.createStatement()) {
                SQLException denied = assertThrows(SQLException.class, () -> statement.execute(sql), sql);
                assertEquals("42501", denied.getSQLState(), sql);
            }
        }
        assertEquals(before, value("SELECT row_to_json(d)::text FROM vra.outbox_delivery d WHERE event_id='" + event + "'"));
        assertTrue(delivery.complete(claim)); // positive intended finalize
    }

    private static ClaimedDelivery claim(TargetCode target) {
        return only(delivery.claim(target, "worker-c", 1, Duration.ofSeconds(30)));
    }

    private static ClaimedDelivery only(List<ClaimedDelivery> claims) {
        assertEquals(1, claims.size());
        return claims.getFirst();
    }

    private static void expire(UUID event) throws SQLException {
        owner("UPDATE vra.outbox_delivery SET claim_until=statement_timestamp()-interval '1 second' "
                + "WHERE event_id='" + event + "'");
    }

    private static UUID fixture(TargetCode target) throws SQLException {
        UUID reservation = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        owner("INSERT INTO vra.inventory_balance VALUES ('" + sku + "','" + ownerId + "','" + location
                + "','AVAILABLE',10,1,1)");
        owner("INSERT INTO vra.inventory_reservation VALUES ('" + reservation + "','" + sku + "','"
                + ownerId + "','" + location + "','AVAILABLE',1,statement_timestamp())");
        try (Connection runtime = connection("vra_runtime"); PreparedStatement statement = runtime.prepareStatement(
                "SELECT vra.async_publish_reservation(?::uuid,?::uuid,?::varchar)")) {
            statement.setObject(1, event);
            statement.setObject(2, reservation);
            statement.setString(3, target.name());
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(event, rows.getObject(1, UUID.class));
            }
        }
        return event;
    }

    private static int count(String sql) throws SQLException {
        return Integer.parseInt(value(sql));
    }

    private static String value(String sql) throws SQLException {
        try (Connection admin = connection("postgres")) {
            return scalar(admin, sql);
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
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
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(root.resolve(script)), "/tmp/stage-c-bootstrap.sql");
        var result = POSTGRES.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                "-v", "runtime_password=" + PASSWORD, "-v", "outbox_worker_password=" + PASSWORD,
                "-v", "reconciliation_worker_password=" + PASSWORD,
                "-v", "async_operator_password=" + PASSWORD,
                "-v", "projection_rebuilder_password=" + PASSWORD,
                "-v", "async_observer_password=" + PASSWORD,
                "-f", "/tmp/stage-c-bootstrap.sql");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }
}
