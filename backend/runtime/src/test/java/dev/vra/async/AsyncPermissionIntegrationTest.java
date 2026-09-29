package dev.vra.async;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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

/** Effective G10 SQL behavior under each POC credential. Catalog checks live in Stage A. */
@Tag("postgres")
class AsyncPermissionIntegrationTest {
    private static final String PASSWORD = "stage-j-permission-disposable";
    private static final String ASYNC_PASSWORD = "async-disposable-test-only";
    private static final String A = "RESERVATION_PROJECTION";
    private static final String B = "VALIDATION_EXTERNAL_EFFECT";
    private static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static final List<String> ORDINARY = List.of("vra_runtime", "vra_outbox_worker",
            "vra_reconciliation_worker", "vra_async_operator", "vra_projection_rebuilder",
            "vra_async_observer");

    @BeforeAll static void start() throws Exception {
        try {
            DB.start();
            Path root = Path.of("").toAbsolutePath();
            while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
                root = root.getParent();
                if (root == null) throw new IllegalStateException("Repository root missing");
            }
            DB.copyFileToContainer(MountableFile.forHostPath(root.resolve("validation/poc-01/db/bootstrap.sql")),
                    "/tmp/stage-j-permission-bootstrap.sql");
            var setup = DB.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                    "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                    "-v", "runtime_password=" + PASSWORD, "-f", "/tmp/stage-j-permission-bootstrap.sql");
            assertEquals(0, setup.getExitCode(), setup.getStderr());
            AsyncRoleBootstrap.run(DB);
            assertEquals(3, new MigrationRunner().migrate(DB.getJdbcUrl(), "vra_migrator", PASSWORD));
        } catch (Exception error) { DB.stop(); throw error; }
    }
    @AfterAll static void stop() { DB.stop(); }
    @BeforeEach void clear() throws SQLException { owner("TRUNCATE vra.outbox_event CASCADE"); }

    @Test void eachWorkloadCredentialExecutesItsNarrowPositiveOperation() throws Exception {
        for (String role : ORDINARY) assertEquals(role, call(role, "SELECT current_user"));
        UUID projection = fixture(A); // actual vra_runtime producer function
        String token = claim(A);
        assertEquals("t", call("vra_outbox_worker", "SELECT vra.async_complete_delivery('" + projection
                + "','" + token + "')"));
        assertEquals("SUCCEEDED", state(projection));
        assertEquals("1", call("vra_async_observer", "SELECT count(*) FROM vra.outbox_delivery "
                + "WHERE event_id='" + projection + "'"));

        UUID failed = fixture(A);
        String failedToken = claim(A);
        assertEquals("t", call("vra_outbox_worker", "SELECT vra.async_fail_delivery('" + failed + "','"
                + failedToken + "','NON_RETRYABLE','NON_RETRYABLE')"));
        assertEquals("t", call("vra_async_operator", "SELECT vra.async_control_replay('" + failed
                + "','CONTROLLED_REPLAY','reviewed')"));
        assertEquals("READY", state(failed));
        assertEquals("2:0", call("vra_projection_rebuilder", "SELECT inserted||':'||repaired "
                + "FROM vra.async_rebuild_reservation_projection()"));

        UUID external = fixture(B);
        String externalToken = claim(B);
        String caseId = call("vra_outbox_worker", "SELECT vra.async_handoff_unknown('" + external + "','"
                + externalToken + "','UNKNOWN_EXTERNAL_RESULT')");
        String caseToken = call("vra_reconciliation_worker", "SELECT claim_token FROM "
                + "vra.async_claim_reconciliation('permission-reconciler',1,30000)");
        assertEquals("t", call("vra_reconciliation_worker", "SELECT vra.async_confirm_external_success('"
                + caseId + "','" + caseToken + "')"));
        assertEquals("SUCCEEDED", state(external));
        try (Connection migrator = connection("vra_migrator"); Statement statement = migrator.createStatement()) {
            statement.execute("SET ROLE vra_owner");
            try (ResultSet result = statement.executeQuery("SELECT current_user")) {
                assertTrue(result.next());
                assertEquals("vra_owner", result.getString(1));
            }
        }
    }

    @Test void forbiddenSqlHasExpectedSqlstateAndLeavesAuthoritativeRowsUnchanged() throws Exception {
        UUID event = fixture(A);
        String reservation = call("postgres", "SELECT reservation_id FROM vra.outbox_event WHERE event_id='"
                + event + "'");
        String before = snapshot(event);
        denied("vra_runtime", "SELECT * FROM vra.async_claim_delivery('" + A + "','runtime',1,30000)", "42501");
        denied("vra_runtime", "SELECT vra.async_control_replay('" + event
                + "','CONTROLLED_REPLAY','runtime')", "42501");
        denied("vra_runtime", "INSERT INTO vra.outbox_delivery(event_id,target_code,state,"
                + "cycle_claim_limit,created_at,state_changed_at) VALUES (gen_random_uuid(),'" + A
                + "','SUCCEEDED',5,now(),now())", "42501");
        denied("vra_outbox_worker", "UPDATE vra.inventory_balance SET reserved=reserved+1", "42501");
        denied("vra_outbox_worker", "UPDATE vra.inventory_reservation SET quantity=2", "42501");
        denied("vra_outbox_worker", "UPDATE vra.inventory_reservation_idempotency SET outcome_status=NULL",
                "42501");
        denied("vra_outbox_worker", "UPDATE vra.outbox_delivery SET cycle_claim_count=4", "42501");
        denied("vra_outbox_worker", "UPDATE vra.outbox_event SET reservation_id='" + reservation + "'",
                "42501");
        denied("vra_outbox_worker", "UPDATE vra.outbox_delivery_history SET action_code='FAILED'", "42501");
        denied("vra_outbox_worker", "SELECT vra.async_control_replay('" + event
                + "','CONTROLLED_REPLAY','worker')", "42501");
        denied("vra_outbox_worker", "SELECT vra.async_rebuild_reservation_projection()", "42501");
        denied("vra_reconciliation_worker", "SELECT * FROM vra.async_claim_delivery('" + A
                + "','reconciler',1,30000)", "42501");
        denied("vra_reconciliation_worker", "UPDATE vra.reconciliation_case SET cycle_claim_limit=16",
                "42501");
        denied("vra_reconciliation_worker", "UPDATE vra.reconciliation_history SET reason_code='BAD'",
                "42501");
        denied("vra_async_operator", "UPDATE vra.outbox_delivery SET cycle_claim_limit=16", "42501");
        denied("vra_projection_rebuilder", "SELECT * FROM vra.outbox_event", "42501");
        denied("vra_async_observer", "UPDATE vra.outbox_delivery SET state='SUCCEEDED'", "42501");
        denied("vra_async_observer", "SELECT * FROM vra.outbox_event", "42501");
        for (String role : ORDINARY) {
            denied(role, "DELETE FROM vra.outbox_delivery_history", "42501");
            denied(role, "TRUNCATE vra.outbox_delivery CASCADE", "42501");
            denied(role, "CREATE TABLE vra.unauthorized(id int)", "42501");
            denied(role, "SET ROLE vra_owner", "42501");
            denied(role, "SET ROLE vra_async_executor", "42501");
            assertEquals("f", call("postgres", "SELECT pg_has_role('" + role +
                    "','vra_owner','SET') OR pg_has_role('" + role + "','vra_async_executor','SET')"));
        }
        assertEquals("f", call("postgres", "SELECT to_regprocedure('vra.async_set_cycle_limit(uuid,int)') "
                + "IS NOT NULL"));
        denied("vra_outbox_worker", "SELECT vra.async_set_cycle_limit('" + event + "',16)", "42883");
        assertEquals(before, snapshot(event));
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
                + "','permission-worker',1,30000)");
    }
    private static String state(UUID event) throws SQLException {
        return call("postgres", "SELECT state FROM vra.outbox_delivery WHERE event_id='" + event + "'");
    }
    private static String snapshot(UUID event) throws SQLException {
        return call("postgres", "SELECT row_to_json(d)::text FROM vra.outbox_delivery d WHERE event_id='"
                + event + "'") + "|" + call("postgres", "SELECT count(*) FROM vra.outbox_delivery_history "
                + "WHERE event_id='" + event + "'");
    }
    private static void denied(String role, String sql, String state) throws SQLException {
        try (Connection db = connection(role); Statement statement = db.createStatement()) {
            SQLException error = assertThrows(SQLException.class, () -> statement.execute(sql), role + " " + sql);
            assertEquals(state, error.getSQLState(), role + " " + sql);
        }
    }
    private static void owner(String sql) throws SQLException {
        try (Connection db = connection("postgres"); Statement statement = db.createStatement()) {
            statement.execute("SET ROLE vra_owner");
            statement.execute(sql);
        }
    }
    private static String call(String role, String sql) throws SQLException {
        try (Connection db = connection(role); Statement statement = db.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), sql);
            String value = result.getString(1);
            assertFalse(result.next(), sql);
            return value;
        }
    }
    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), role,
                role.equals("postgres") || role.equals("vra_runtime") || role.equals("vra_migrator")
                        ? PASSWORD : ASYNC_PASSWORD);
    }
}
