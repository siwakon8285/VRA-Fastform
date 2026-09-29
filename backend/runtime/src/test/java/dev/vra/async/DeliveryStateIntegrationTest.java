package dev.vra.async;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

/** Stage-J G9 edge checks complement the retained V3 constraint and Stage-C/G matrices. */
@Tag("postgres")
class DeliveryStateIntegrationTest {
    private static final String PASSWORD = "stage-j-state-disposable";
    private static final String ASYNC_PASSWORD = "async-disposable-test-only";
    private static final String A = "RESERVATION_PROJECTION";
    private static final String B = "VALIDATION_EXTERNAL_EFFECT";
    private static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);

    @BeforeAll static void start() throws Exception {
        try {
            DB.start();
            Path root = Path.of("").toAbsolutePath();
            while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
                root = root.getParent();
                if (root == null) throw new IllegalStateException("Repository root missing");
            }
            DB.copyFileToContainer(MountableFile.forHostPath(root.resolve("validation/poc-01/db/bootstrap.sql")),
                    "/tmp/stage-j-state-bootstrap.sql");
            var setup = DB.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                    "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                    "-v", "runtime_password=" + PASSWORD, "-f", "/tmp/stage-j-state-bootstrap.sql");
            assertEquals(0, setup.getExitCode(), setup.getStderr());
            AsyncRoleBootstrap.run(DB);
            assertEquals(3, new MigrationRunner().migrate(DB.getJdbcUrl(), "vra_migrator", PASSWORD));
        } catch (Exception error) { DB.stop(); throw error; }
    }
    @AfterAll static void stop() { DB.stop(); }
    @BeforeEach void clear() throws SQLException { owner("TRUNCATE vra.outbox_event CASCADE"); }

    @Test void readyRetryFailReplaySuccessAndTerminalGuardsPreserveCycleHistory() throws Exception {
        UUID event = fixture(A);
        assertEquals("READY:0:5", value("SELECT state||':'||cycle_claim_count||':'||cycle_claim_limit "
                + "FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
        String first = claim(A);
        assertEquals("RETRY_WAIT", call("vra_outbox_worker", "SELECT vra.async_retry_delivery('" + event
                + "','" + first + "','RETRYABLE_TRANSIENT',1000)"));
        owner("UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-interval '1 second' "
                + "WHERE event_id='" + event + "'");
        String second = claim(A);
        assertNotEquals(first, second);
        assertEquals("t", call("vra_outbox_worker", "SELECT vra.async_fail_delivery('" + event + "','"
                + second + "','NON_RETRYABLE','NON_RETRYABLE')"));
        // A failed ordinary delivery is replayable; its old claim and history stay fenced.
        assertEquals("t", call("vra_async_operator", "SELECT vra.async_control_replay('" + event
                + "','CONTROLLED_REPLAY','reviewed')"));
        assertEquals("READY:2:0:5:2", value("SELECT state||':'||automatic_cycle||':'||"
                + "cycle_claim_count||':'||cycle_claim_limit||':'||delivery_attempt_count "
                + "FROM vra.outbox_delivery WHERE event_id='" + event + "'"));
        String third = claim(A);
        assertEquals("t", call("vra_outbox_worker", "SELECT vra.async_complete_delivery('" + event
                + "','" + third + "')"));
        assertEquals("SUCCEEDED", state(event));
        assertEquals("f", call("vra_outbox_worker", "SELECT vra.async_complete_delivery('" + event
                + "','" + third + "')"));
        assertNull(call("vra_outbox_worker", "SELECT vra.async_retry_delivery('" + event + "','"
                + third + "','RETRYABLE_TRANSIENT',1)"));
        assertEquals("f", call("vra_outbox_worker", "SELECT vra.async_relinquish_delivery('" + event
                + "','" + third + "')"));
        assertEquals("f", call("vra_async_operator", "SELECT vra.async_control_replay('" + event
                + "','CONTROLLED_REPLAY','late')"));
        assertEquals("0", value("SELECT count(*) FROM vra.async_claim_delivery('" + A
                + "','terminal',1,30000)"));
        assertEquals("1", value("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event
                + "' AND action_code='CONTROL_REPLAY' AND automatic_cycle=2 AND cycle_claim_count=0 "
                + "AND cycle_claim_limit=5 AND lifetime_attempt=2"));
        assertEquals("3", value("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='" + event
                + "' AND action_code='CLAIM'"));
    }

    @Test void reconciliationEdgesUseCurrentCaseTokenAndPairedDeliveryTransitions() throws Exception {
        UUID noEffect = fixture(B);
        String noEffectCase = handoff(noEffect);
        String first = claimCase();
        assertEquals("WAITING", call("vra_reconciliation_worker", "SELECT vra.async_wait_reconciliation('"
                + noEffectCase + "','" + first + "','UNKNOWN_EXTERNAL_RESULT',1000)"));
        owner("UPDATE vra.reconciliation_case SET next_eligible_at=statement_timestamp()-interval '1 second' "
                + "WHERE case_id='" + noEffectCase + "'");
        String second = claimCase();
        assertNotEquals(first, second);
        assertEquals("f", call("vra_reconciliation_worker", "SELECT vra.async_confirm_external_success('"
                + noEffectCase + "','" + first + "')"));
        assertEquals("RETRY_WAIT", call("vra_reconciliation_worker", "SELECT "
                + "vra.async_confirm_external_no_effect('" + noEffectCase + "','" + second + "',true,1000)"));
        assertEquals("RESOLVED:CONFIRMED_NO_EFFECT", caseState(noEffectCase));
        assertEquals("RETRY_WAIT", state(noEffect));
        owner("UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-interval '1 second' "
                + "WHERE event_id='" + noEffect + "'");
        String retry = claim(B);
        String laterCase = call("vra_outbox_worker", "SELECT vra.async_handoff_unknown('" + noEffect + "','"
                + retry + "','UNKNOWN_EXTERNAL_RESULT')");
        assertNotEquals(noEffectCase, laterCase);
        String later = claimCase();
        assertEquals("t", call("vra_reconciliation_worker", "SELECT vra.async_confirm_external_success('"
                + laterCase + "','" + later + "')"));
        assertEquals("SUCCEEDED", state(noEffect));
        assertEquals("RESOLVED:CONFIRMED_SUCCEEDED", caseState(laterCase));
        assertEquals("f", call("vra_reconciliation_worker", "SELECT vra.async_confirm_external_success('"
                + laterCase + "','" + later + "')"));

        UUID unknown = fixture(B);
        String unknownCase = handoff(unknown);
        String unknownToken = claimCase();
        assertEquals("t", call("vra_reconciliation_worker", "SELECT vra.async_exhaust_reconciliation('"
                + unknownCase + "','" + unknownToken + "','RECONCILIATION_EXHAUSTED')"));
        assertEquals("FAILED", state(unknown));
        assertEquals("OPERATOR_REQUIRED:UNKNOWN", caseState(unknownCase));
        assertEquals("t", call("vra_async_operator", "SELECT vra.async_control_resume('" + unknownCase
                + "','CONTROLLED_RESUME','reviewed')"));
        assertEquals("RECONCILIATION_REQUIRED", state(unknown));
        assertEquals("PENDING:UNKNOWN", caseState(unknownCase));
        assertEquals("2:0:4:1", value("SELECT automatic_cycle||':'||cycle_claim_count||':'||"
                + "cycle_claim_limit||':'||lifetime_attempt_count FROM vra.reconciliation_case WHERE case_id='"
                + unknownCase + "'"));
        String resumed = claimCase();
        assertEquals("t", call("vra_reconciliation_worker", "SELECT vra.async_exhaust_reconciliation('"
                + unknownCase + "','" + resumed + "','RECONCILIATION_EXHAUSTED')"));
        assertEquals("t", call("vra_async_operator", "SELECT vra.async_control_close('" + unknown
                + "','CONTROLLED_CLOSE','reviewed')"));
        assertEquals("CLOSED:UNKNOWN", caseState(unknownCase));
        assertEquals("CLOSED", state(unknown));
        assertEquals("f", call("vra_async_operator", "SELECT vra.async_control_resume('" + unknownCase
                + "','CONTROLLED_RESUME','late')"));
    }

    @Test void malformedShapeAndOrphanAreDeniedWithoutChangingAuthoritativeState() throws Exception {
        UUID event = fixture(B);
        String before = state(event);
        for (String assignment : new String[] {"cycle_claim_limit=0", "cycle_claim_limit=17",
                "cycle_claim_count=-1", "state='PROCESSING'", "state='RECONCILIATION_REQUIRED'"}) {
            try (Connection db = connection("postgres"); Statement statement = db.createStatement()) {
                statement.execute("SET ROLE vra_owner");
                SQLException denied = assertThrows(SQLException.class, () -> statement.execute(
                        "UPDATE vra.outbox_delivery SET " + assignment + " WHERE event_id='" + event + "'"));
                assertEquals("23514", denied.getSQLState(), assignment);
            }
            assertEquals(before, state(event));
        }
        assertEquals("0", value("SELECT count(*) FROM pg_proc WHERE pronamespace='vra'::regnamespace "
                + "AND proname ~* '(set_state|set_budget|set_cycle_limit)'"));
        assertEquals("0", value("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='" + event + "'"));
    }

    private static UUID fixture(String target) throws SQLException {
        UUID event = UUID.randomUUID(), reservation = UUID.randomUUID(), sku = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID(), location = UUID.randomUUID();
        owner("INSERT INTO vra.inventory_balance VALUES ('" + sku + "','" + ownerId + "','" + location
                + "','AVAILABLE',10,1,1)");
        owner("INSERT INTO vra.inventory_reservation VALUES ('" + reservation + "','" + sku + "','"
                + ownerId + "','" + location + "','AVAILABLE',1,statement_timestamp())");
        assertEquals(event.toString(), call("vra_runtime", "SELECT vra.async_publish_reservation('"
                + event + "','" + reservation + "','" + target + "')"));
        return event;
    }
    private static String claim(String target) throws SQLException {
        return call("vra_outbox_worker", "SELECT claim_token FROM vra.async_claim_delivery('" + target
                + "','state-matrix',1,30000)");
    }
    private static String handoff(UUID event) throws SQLException {
        String token = claim(B);
        return call("vra_outbox_worker", "SELECT vra.async_handoff_unknown('" + event + "','" + token
                + "','UNKNOWN_EXTERNAL_RESULT')");
    }
    private static String claimCase() throws SQLException {
        return call("vra_reconciliation_worker", "SELECT claim_token FROM "
                + "vra.async_claim_reconciliation('state-matrix',1,30000)");
    }
    private static String caseState(String caseId) throws SQLException {
        return value("SELECT state||':'||external_knowledge FROM vra.reconciliation_case WHERE case_id='"
                + caseId + "'");
    }
    private static String state(UUID event) throws SQLException {
        return value("SELECT state FROM vra.outbox_delivery WHERE event_id='" + event + "'");
    }
    private static String value(String sql) throws SQLException { return call("postgres", sql); }
    private static String call(String role, String sql) throws SQLException {
        try (Connection db = connection(role); Statement statement = db.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), sql);
            String value = result.getString(1);
            assertFalse(result.next(), sql);
            return value;
        }
    }
    private static void owner(String sql) throws SQLException {
        try (Connection db = connection("postgres"); Statement statement = db.createStatement()) {
            statement.execute("SET ROLE vra_owner");
            statement.execute(sql);
        }
    }
    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), role,
                role.equals("postgres") || role.equals("vra_runtime") ? PASSWORD : ASYNC_PASSWORD);
    }
}
