                        package dev.vra.async;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import dev.vra.async.adapter.out.visibility.JdbcAsyncVisibility;
import dev.vra.async.adapter.out.visibility.JdbcAsyncVisibility.Activity;
import dev.vra.async.adapter.out.visibility.JdbcAsyncVisibility.StateCount;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

/** Observer results are compared with independent PostgreSQL authority, not JVM clocks. */
@Tag("postgres")
class AsyncVisibilityIntegrationTest {
    private static final String PASSWORD = "stage-j-disposable-only";
    private static final String ASYNC_PASSWORD = "async-disposable-test-only";
    private static final String PROJECTION = "RESERVATION_PROJECTION";
    private static final String EXTERNAL = "VALIDATION_EXTERNAL_EFFECT";
    private static final PostgreSQLContainer DB = new dev.vra.poc04.external.RunOwnedPostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static JdbcAsyncVisibility visibility;

    private record Fixture(UUID eventId, UUID reservationId, UUID skuId) {}

    @BeforeAll static void start() throws Exception {
        try {
            DB.start();
            Path root = Path.of("").toAbsolutePath();
            while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
                root = root.getParent();
                if (root == null) throw new IllegalStateException("Repository root missing");
            }
            DB.copyFileToContainer(MountableFile.forHostPath(root.resolve("validation/poc-01/db/bootstrap.sql")),
                    "/tmp/stage-j-bootstrap.sql");
            var setup = DB.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                    "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                    "-v", "runtime_password=" + PASSWORD, "-f", "/tmp/stage-j-bootstrap.sql");
            assertEquals(0, setup.getExitCode(), setup.getStderr());
            AsyncRoleBootstrap.run(DB);
            assertEquals(4, new MigrationRunner().migrate(DB.getJdbcUrl(), "vra_migrator", PASSWORD));
            visibility = new JdbcAsyncVisibility(JdbcClient.create(new DriverManagerDataSource(
                    DB.getJdbcUrl(), "vra_async_observer", ASYNC_PASSWORD)));
            assertEquals("vra_async_observer", read("vra_async_observer", "SELECT current_user"));
        } catch (Exception failure) {
            DB.stop();
            throw failure;
        }
    }

    @AfterAll static void stop() { DB.stop(); }
    @BeforeEach void clear() throws SQLException { owner("TRUNCATE vra.outbox_event CASCADE"); }

    @Test void allCurrentStatesActivityAgeAndSafeHistoryMatchPostgresql() throws Exception {
        Fixture processing = fixture(PROJECTION);
        claim(processing);
        Fixture retry = fixture(PROJECTION);
        String retryToken = claim(retry);
        assertEquals("RETRY_WAIT", call("vra_outbox_worker", "SELECT vra.async_retry_delivery('"
                + retry.eventId + "','" + retryToken + "','RETRYABLE_TRANSIENT',5000)"));
        Fixture succeeded = fixture(PROJECTION);
        String successToken = claim(succeeded);
        assertEquals("t", call("vra_outbox_worker", "SELECT vra.async_complete_delivery('"
                + succeeded.eventId + "','" + successToken + "')"));
        Fixture failed = fixture(PROJECTION);
        String failedToken = claim(failed);
        assertEquals("t", call("vra_outbox_worker", "SELECT vra.async_fail_delivery('"
                + failed.eventId + "','" + failedToken + "','NON_RETRYABLE','NON_RETRYABLE')"));
        Fixture ready = fixture(PROJECTION);
        Fixture checking = fixture(EXTERNAL);
        String checkingCase = handoff(checking);
        claimCase(checkingCase);
        Fixture waiting = fixture(EXTERNAL);
        String waitingCase = handoff(waiting);
        String waitingToken = claimCase(waitingCase);
        assertEquals("WAITING", call("vra_reconciliation_worker", "SELECT vra.async_wait_reconciliation('"
                + waitingCase + "','" + waitingToken + "','UNKNOWN_EXTERNAL_RESULT',5000)"));
        Fixture required = fixture(EXTERNAL);
        String requiredCase = handoff(required);
        String requiredToken = claimCase(requiredCase);
        assertEquals("t", call("vra_reconciliation_worker", "SELECT vra.async_exhaust_reconciliation('"
                + requiredCase + "','" + requiredToken + "','RECONCILIATION_EXHAUSTED')"));
        Fixture pending = fixture(EXTERNAL);
        String pendingCase = handoff(pending);

        assertState(PROJECTION, "READY", 1, visibility.deliveryBacklog());
        assertState(PROJECTION, "PROCESSING", 1, visibility.deliveryBacklog());
        assertState(PROJECTION, "RETRY_WAIT", 1, visibility.deliveryBacklog());
        assertState(PROJECTION, "FAILED", 1, visibility.deliveryBacklog());
        assertState(EXTERNAL, "RECONCILIATION_REQUIRED", 3, visibility.deliveryBacklog());
        assertState(EXTERNAL, "FAILED", 1, visibility.deliveryBacklog());
        assertCaseState("PENDING", 1);
        assertCaseState("CHECKING", 1);
        assertCaseState("WAITING", 1);
        assertCaseState("OPERATOR_REQUIRED", 1);
        assertEquals("SUCCEEDED", read("postgres", "SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + succeeded.eventId + "'"));
        assertActivity(PROJECTION, "SUCCEEDED", visibility.deliveryActivity());
        assertActivity(PROJECTION, "RETRY_SCHEDULED", visibility.deliveryActivity());
        assertActivity(PROJECTION, "FAILED", visibility.deliveryActivity());
        assertCaseActivity(EXTERNAL, "EXHAUSTED");
        for (String target : List.of(PROJECTION, EXTERNAL)) {
            long before = dbAge(target);
            var age = visibility.oldestOutstandingAge().stream()
                    .filter(row -> row.targetCode().equals(target)).findFirst().orElseThrow();
            long after = dbAge(target);
            assertTrue(age.oldestAgeMillis() >= before - 2 && age.oldestAgeMillis() <= after + 2,
                    target + " age=" + age.oldestAgeMillis() + " DB=" + before + ".." + after);
            assertEquals(Long.parseLong(read("postgres", "SELECT count(*) FROM vra.outbox_delivery WHERE "
                    + "target_code='" + target + "' AND state NOT IN ('SUCCEEDED','CLOSED')")), age.count());
        }
        assertEquals(1, visibility.deliveryHistory(ready.eventId).size());
        var retryHistory = visibility.deliveryHistory(retry.eventId);
        assertEquals("RETRY_SCHEDULED", retryHistory.getLast().action());
        assertEquals(1, retryHistory.getLast().cycleClaimCount());
        assertEquals(5, retryHistory.getLast().cycleClaimLimit());
        assertEquals(1, retryHistory.getLast().lifetimeAttempt());
        assertEquals("RETRYABLE_TRANSIENT", retryHistory.getLast().reasonCode());
        var caseHistory = visibility.reconciliationHistory(UUID.fromString(requiredCase));
        assertEquals("EXHAUSTED", caseHistory.getLast().action());
        assertEquals("RECONCILIATION_EXHAUSTED", caseHistory.getLast().reasonCode());
        assertEquals("EXHAUSTED:1:4:1", read("postgres", "SELECT action_code||':'||cycle_claim_count||':'||"
                + "cycle_claim_limit||':'||lifetime_attempt FROM vra.reconciliation_history WHERE case_id='"
                + requiredCase + "' ORDER BY history_id DESC LIMIT 1"));
        assertFalse(visibility.reconciliationHistory(UUID.fromString(pendingCase)).isEmpty());
        assertFalse(Arrays.stream(JdbcAsyncVisibility.DeliveryHistory.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("claimToken")));
        assertFalse(Arrays.stream(JdbcAsyncVisibility.ReconciliationHistory.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("claimToken")));
        String deliveryToken = read("postgres", "SELECT claim_token::text FROM vra.outbox_delivery "
                + "WHERE event_id='" + processing.eventId + "'");
        String reconciliationToken = read("postgres", "SELECT claim_token::text FROM vra.reconciliation_case "
                + "WHERE case_id='" + checkingCase + "'");
        assertFalse(visibility.deliveryHistory(processing.eventId).toString().contains(deliveryToken));
        assertFalse(visibility.reconciliationHistory(UUID.fromString(checkingCase)).toString()
                .contains(reconciliationToken));
        String rendered = visibility.deliveryBacklog() + " " + visibility.reconciliationBacklog() + " "
                + visibility.deliveryActivity() + " " + retryHistory + " " + caseHistory;
        for (Fixture secret : List.of(ready, processing, retry, succeeded, failed, pending, checking,
                waiting, required)) {
            assertFalse(rendered.contains(secret.reservationId.toString()));
            assertFalse(rendered.contains(secret.skuId.toString()));
        }
        assertFalse(rendered.contains(PASSWORD));
        assertFalse(rendered.contains(ASYNC_PASSWORD));
        assertFalse(rendered.contains("raw exception"));
    }

    @Test void normalAndRecoveryOnlyExhaustionRemainDistinctInImmutableHistory() throws Exception {
        Fixture deliveryNormal = fixture(PROJECTION);
        for (int i = 1; i <= 5; i++) {
            String token = claim(deliveryNormal);
            assertEquals(i, Integer.parseInt(read("postgres", "SELECT cycle_claim_count FROM "
                    + "vra.outbox_delivery WHERE event_id='" + deliveryNormal.eventId + "'")));
            call("vra_outbox_worker", "SELECT vra.async_retry_delivery('" + deliveryNormal.eventId
                    + "','" + token + "','RETRYABLE_TRANSIENT',1)");
            if (i < 5) owner("UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-"
                    + "interval '1 second' WHERE event_id='" + deliveryNormal.eventId + "'");
        }
        Fixture deliveryRecovery = fixture(PROJECTION);
        for (int i = 1; i <= 5; i++) {
            claim(deliveryRecovery);
            owner("UPDATE vra.outbox_delivery SET claim_until=statement_timestamp()-interval '1 second' "
                    + "WHERE event_id='" + deliveryRecovery.eventId + "'");
        }
        assertEquals("", call("vra_outbox_worker", "SELECT COALESCE((SELECT event_id::text FROM "
                + "vra.async_claim_delivery('" + PROJECTION + "','visibility-recovery',1,30000)), '')"));
        assertEquals("FAILED", read("postgres", "SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + deliveryNormal.eventId + "'"));
        assertEquals("FAILED", read("postgres", "SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + deliveryRecovery.eventId + "'"));
        assertEquals("FAILED", visibility.deliveryHistory(deliveryNormal.eventId).getLast().action());
        var recovery = visibility.deliveryHistory(deliveryRecovery.eventId);
        assertEquals("RECOVERY_ONLY_FAILED", recovery.getLast().action());
        assertEquals(6, recovery.getLast().cycleClaimCount());
        assertEquals(5, recovery.getLast().cycleClaimLimit());
        assertEquals(6, recovery.getLast().lifetimeAttempt());
        assertEquals("RETRY_EXHAUSTED", recovery.getLast().reasonCode());

        Fixture caseNormal = fixture(EXTERNAL);
        String normalId = handoff(caseNormal);
        for (int i = 1; i <= 4; i++) {
            String token = claimCase(normalId);
            assertEquals("WAITING".equals(call("vra_reconciliation_worker", "SELECT "
                    + "vra.async_wait_reconciliation('" + normalId + "','" + token
                    + "','UNKNOWN_EXTERNAL_RESULT',1)")), i < 4);
            if (i < 4) owner("UPDATE vra.reconciliation_case SET next_eligible_at=statement_timestamp()-"
                    + "interval '1 second' WHERE case_id='" + normalId + "'");
        }
        Fixture caseRecovery = fixture(EXTERNAL);
        String recoveryId = handoff(caseRecovery);
        for (int i = 1; i <= 4; i++) {
            claimCase(recoveryId);
            owner("UPDATE vra.reconciliation_case SET claim_until=statement_timestamp()-"
                    + "interval '1 second' WHERE case_id='" + recoveryId + "'");
        }
        assertEquals("", call("vra_reconciliation_worker", "SELECT COALESCE((SELECT case_id::text FROM "
                + "vra.async_claim_reconciliation('visibility-recovery',1,30000)), '')"));
        assertEquals("EXHAUSTED", visibility.reconciliationHistory(normalIdUuid(normalId)).getLast().action());
        var caseRecoveryHistory = visibility.reconciliationHistory(normalIdUuid(recoveryId));
        assertEquals("RECOVERY_ONLY_EXHAUSTED", caseRecoveryHistory.getLast().action());
        assertEquals(5, caseRecoveryHistory.getLast().cycleClaimCount());
        assertEquals(4, caseRecoveryHistory.getLast().cycleClaimLimit());
        assertEquals(5, caseRecoveryHistory.getLast().lifetimeAttempt());
        assertEquals("OPERATOR_REQUIRED", read("postgres", "SELECT state FROM vra.reconciliation_case "
                + "WHERE case_id='" + recoveryId + "'"));
    }

    @Test void observerCanReadButCannotMutateOrEscalate() throws Exception {
        Fixture fixture = fixture(PROJECTION);
        assertThrows(IllegalStateException.class, () -> new JdbcAsyncVisibility(JdbcClient.create(
                new DriverManagerDataSource(DB.getJdbcUrl(), "vra_outbox_worker", ASYNC_PASSWORD))));
        assertEquals(1, visibility.deliveryHistory(fixture.eventId).size());
        String before = read("postgres", "SELECT state||':'||cycle_claim_count FROM vra.outbox_delivery "
                + "WHERE event_id='" + fixture.eventId + "'");
        for (String sql : List.of("UPDATE vra.outbox_delivery SET cycle_claim_limit=16",
                "DELETE FROM vra.outbox_delivery_history", "TRUNCATE vra.outbox_delivery CASCADE",
                "INSERT INTO vra.outbox_delivery DEFAULT VALUES", "CREATE TABLE vra.forbidden(id int)",
                "SELECT vra.async_claim_delivery('" + PROJECTION + "','observer',1,30000)",
                "SET ROLE vra_owner", "SET ROLE vra_async_executor")) {
            try (Connection observer = connection("vra_async_observer"); Statement statement = observer.createStatement()) {
                SQLException denied = assertThrows(SQLException.class, () -> statement.execute(sql), sql);
                assertEquals("42501", denied.getSQLState(), sql);
            }
            assertEquals(before, read("postgres", "SELECT state||':'||cycle_claim_count FROM vra.outbox_delivery "
                    + "WHERE event_id='" + fixture.eventId + "'"));
            assertEquals(1, visibility.deliveryHistory(fixture.eventId).size());
        }
        assertEquals("f", read("postgres", "SELECT has_table_privilege('vra_async_observer',"
                + "'vra.outbox_event','SELECT')"));
    }

    private static UUID normalIdUuid(String id) { return UUID.fromString(id); }

    private static void assertCaseState(String state, long expected) throws SQLException {
        assertEquals(expected, visibility.reconciliationBacklog().stream()
                .filter(row -> row.targetCode().equals(EXTERNAL) && row.state().equals(state))
                .mapToLong(StateCount::count).sum());
        assertEquals(Long.toString(expected), read("postgres", "SELECT count(*) FROM vra.reconciliation_case c "
                + "JOIN vra.outbox_delivery d USING(event_id) WHERE d.target_code='" + EXTERNAL
                + "' AND c.state='" + state + "'"));
    }

    private static void assertCaseActivity(String target, String action) throws SQLException {
        long reported = visibility.reconciliationActivity().stream()
                .filter(row -> row.targetCode().equals(target) && row.action().equals(action))
                .mapToLong(Activity::count).sum();
        assertTrue(reported > 0);
        assertEquals(Long.parseLong(read("postgres", "SELECT count(*) FROM vra.reconciliation_history h "
                + "JOIN vra.reconciliation_case c USING(case_id) JOIN vra.outbox_delivery d USING(event_id) "
                + "WHERE d.target_code='" + target + "' AND h.action_code='" + action + "'")), reported);
    }

    private static void assertState(String target, String state, long expected, List<StateCount> rows)
            throws SQLException {
        assertEquals(expected, rows.stream().filter(row -> row.targetCode().equals(target)
                && row.state().equals(state)).mapToLong(StateCount::count).sum());
        assertEquals(Long.toString(expected), read("postgres", "SELECT count(*) FROM vra.outbox_delivery "
                + "WHERE target_code='" + target + "' AND state='" + state + "'"));
    }

    private static void assertActivity(String target, String action, List<Activity> rows) throws SQLException {
        long reported = rows.stream().filter(row -> row.targetCode().equals(target)
                && row.action().equals(action)).mapToLong(Activity::count).sum();
        assertTrue(reported > 0, target + " " + action);
        assertEquals(Long.parseLong(read("postgres", "SELECT count(*) FROM vra.outbox_delivery_history h "
                + "JOIN vra.outbox_delivery d USING(event_id) WHERE d.target_code='" + target
                + "' AND h.action_code='" + action + "'")), reported);
    }

    private static long dbAge(String target) throws SQLException {
        return Long.parseLong(read("postgres", "SELECT GREATEST(0,FLOOR(EXTRACT(EPOCH FROM "
                + "(statement_timestamp()-min(created_at)))*1000))::bigint FROM vra.outbox_delivery "
                + "WHERE target_code='" + target + "' AND state NOT IN ('SUCCEEDED','CLOSED')"));
    }

    private static Fixture fixture(String target) throws SQLException {
        UUID event = UUID.randomUUID(), reservation = UUID.randomUUID(), sku = UUID.randomUUID();
        UUID owner = UUID.randomUUID(), location = UUID.randomUUID();
        owner("INSERT INTO vra.inventory_balance VALUES ('" + sku + "','" + owner + "','" + location
                + "','AVAILABLE',10,1,1)");
        owner("INSERT INTO vra.inventory_reservation VALUES ('" + reservation + "','" + sku + "','" + owner
                + "','" + location + "','AVAILABLE',1,statement_timestamp())");
        assertEquals(event.toString(), call("vra_runtime", "SELECT vra.async_publish_reservation('"
                + event + "','" + reservation + "','" + target + "')"));
        return new Fixture(event, reservation, sku);
    }

    private static String claim(Fixture fixture) throws SQLException {
        return call("vra_outbox_worker", "SELECT claim_token FROM vra.async_claim_delivery('"
                + target(fixture) + "','visibility-worker',1,30000)");
    }

    private static String target(Fixture fixture) throws SQLException {
        return read("postgres", "SELECT target_code FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'");
    }

    private static String handoff(Fixture fixture) throws SQLException {
        String token = claim(fixture);
        return call("vra_outbox_worker", "SELECT vra.async_handoff_unknown('" + fixture.eventId
                + "','" + token + "','UNKNOWN_EXTERNAL_RESULT')");
    }

    private static String claimCase(String caseId) throws SQLException {
        String claimed = call("vra_reconciliation_worker", "SELECT case_id::text||':'||claim_token::text "
                + "FROM vra.async_claim_reconciliation('visibility-reconciler',1,30000)");
        assertTrue(claimed.startsWith(caseId + ":"), claimed);
        return claimed.substring(caseId.length() + 1);
    }

    private static void owner(String sql) throws SQLException {
        try (Connection db = connection("postgres"); Statement statement = db.createStatement()) {
            statement.execute("SET ROLE vra_owner");
            statement.execute(sql);
        }
    }

    private static String call(String role, String sql) throws SQLException { return read(role, sql); }

    private static String read(String role, String sql) throws SQLException {
        try (Connection db = connection(role); Statement statement = db.createStatement();
             ResultSet row = statement.executeQuery(sql)) {
            assertTrue(row.next(), sql);
            String value = row.getString(1);
            assertFalse(row.next(), sql);
            return value;
        }
    }

    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), role,
                role.equals("postgres") || role.equals("vra_runtime") ? PASSWORD : ASYNC_PASSWORD);
    }
}
