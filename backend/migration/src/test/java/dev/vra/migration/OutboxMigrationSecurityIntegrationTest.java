package dev.vra.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Stage A database proof only; does not claim application-path G9/G10 coverage. */
@Tag("postgres")
class OutboxMigrationSecurityIntegrationTest {
    private static final String PASSWORD = "poc03-disposable-test-only";
    private static final String RUNTIME = "vra_runtime";
    private static final String WORKER = "vra_outbox_worker";
    private static final String RECONCILER = "vra_reconciliation_worker";
    private static final String OPERATOR = "vra_async_operator";
    private static final String REBUILDER = "vra_projection_rebuilder";
    private static final String OBSERVER = "vra_async_observer";
    private static final String EXECUTOR = "vra_async_executor";
    private static final String OWNER = "vra_owner";
    private static final String[] ORDINARY = {RUNTIME, WORKER, RECONCILER, OPERATOR, REBUILDER, OBSERVER};
    private static final String A = "RESERVATION_PROJECTION";
    private static final String B = "VALIDATION_EXTERNAL_EFFECT";
    private static final String[] TABLES = {"outbox_event", "outbox_delivery", "outbox_delivery_history",
            "consumer_inbox", "reservation_projection", "reconciliation_case", "reconciliation_history"};
    private static final String ID = "'00000000-0000-0000-0000-000000000001'::uuid";
    private static final Map<String, String> CALLERS = Map.ofEntries(
            Map.entry("async_publish_reservation", RUNTIME),
            Map.entry("async_claim_delivery", WORKER), Map.entry("async_relinquish_delivery", WORKER),
            Map.entry("async_complete_delivery", WORKER), Map.entry("async_retry_delivery", WORKER),
            Map.entry("async_fail_delivery", WORKER), Map.entry("async_handoff_unknown", WORKER),
            Map.entry("async_claim_reconciliation", RECONCILER), Map.entry("async_wait_reconciliation", RECONCILER),
            Map.entry("async_confirm_external_success", RECONCILER), Map.entry("async_confirm_external_no_effect", RECONCILER),
            Map.entry("async_exhaust_reconciliation", RECONCILER), Map.entry("async_control_replay", OPERATOR),
            Map.entry("async_control_resume", OPERATOR), Map.entry("async_control_close", OPERATOR),
            Map.entry("async_rebuild_reservation_projection", REBUILDER));
    private static final Map<String, String> ARGUMENTS = Map.ofEntries(
            Map.entry("async_publish_reservation", ID + ",gen_random_uuid(),'" + A + "'"),
            Map.entry("async_claim_delivery", "'" + A + "','test',1,30000::bigint"),
            Map.entry("async_relinquish_delivery", ID + "," + ID),
            Map.entry("async_complete_delivery", ID + "," + ID),
            Map.entry("async_retry_delivery", ID + "," + ID + ",'RETRYABLE_TRANSIENT',1::bigint"),
            Map.entry("async_fail_delivery", ID + "," + ID + ",'POISON','POISON'"),
            Map.entry("async_handoff_unknown", ID + "," + ID + ",'UNKNOWN_EXTERNAL_RESULT'"),
            Map.entry("async_claim_reconciliation", "'test',1,30000::bigint"),
            Map.entry("async_wait_reconciliation", ID + "," + ID + ",'UNKNOWN_EXTERNAL_RESULT',1::bigint"),
            Map.entry("async_confirm_external_success", ID + "," + ID),
            Map.entry("async_confirm_external_no_effect", ID + "," + ID + ",true,1::bigint"),
            Map.entry("async_exhaust_reconciliation", ID + "," + ID + ",'RECONCILIATION_EXHAUSTED'"),
            Map.entry("async_control_replay", ID + ",'CONTROLLED_REPLAY','test'"),
            Map.entry("async_control_resume", ID + ",'CONTROLLED_RESUME','test'"),
            Map.entry("async_control_close", ID + ",'CONTROLLED_CLOSE','test'"),
            Map.entry("async_rebuild_reservation_projection", ""));

    @Test
    void freshMigrationPreservesChecksumsAndCreatesExactCatalog() throws Exception {
        try (Fixture f = fresh()) {
            assertEquals("170011", f.value("SHOW server_version_num"));
            assertEquals("4", f.value("SELECT max(version::int) FROM vra.flyway_schema_history"));
            MigrationRunner runner = new MigrationRunner();
            assertEquals(0, runner.migrate(f.db.getJdbcUrl(), "vra_migrator", PASSWORD));
            runner.validate(f.db.getJdbcUrl(), "vra_migrator", PASSWORD);
            for (String table : TABLES) {
                assertEquals(OWNER, f.value("SELECT tableowner FROM pg_tables WHERE schemaname='vra' AND tablename='" + table + "'"));
                assertTrue(Integer.parseInt(f.value("SELECT count(*) FROM pg_constraint WHERE conrelid='vra." + table + "'::regclass")) > 0);
            }
            for (String index : List.of("outbox_event_reservation_id_key", "outbox_delivery_due_idx",
                    "outbox_delivery_expired_idx", "outbox_delivery_token_uq", "outbox_delivery_backlog_idx",
                    "outbox_delivery_history_event_idx", "outbox_delivery_history_claim_uq", "consumer_inbox_pkey",
                    "reservation_projection_key_idx", "reconciliation_case_active_uq", "reconciliation_case_due_idx",
                    "reconciliation_case_expired_idx", "reconciliation_case_backlog_idx",
                    "reconciliation_history_case_idx", "reconciliation_history_claim_uq")) {
                assertEquals("t", f.value("SELECT to_regclass('vra." + index + "') IS NOT NULL"), index);
            }
            assertEquals("9", f.value("SELECT count(*) FROM pg_trigger WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace='vra'::regnamespace) AND NOT tgisinternal"));
            assertEquals("4", f.value("SELECT count(*) FROM pg_trigger WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace='vra'::regnamespace) AND NOT tgisinternal AND tgdeferrable AND tginitdeferred"));
            Set<String> expectedFunctions = new TreeSet<>(CALLERS.keySet());
            expectedFunctions.addAll(Set.of("async_reject_immutable", "async_guard_delivery_identity", "async_guard_case_identity",
                    "async_check_pair", "async_check_delivery_recovery", "async_check_case_recovery"));
            assertEquals(expectedFunctions, new TreeSet<>(f.column("SELECT proname FROM pg_proc WHERE pronamespace='vra'::regnamespace")));
            assertEquals("22", f.value("SELECT count(*) FROM pg_proc WHERE pronamespace='vra'::regnamespace AND proowner='vra_async_executor'::regrole AND prosecdef AND proconfig=ARRAY['search_path=pg_catalog, pg_temp']"));
            assertEquals("0", f.value("SELECT count(*) FROM pg_proc p,LATERAL aclexplode(p.proacl) a WHERE p.pronamespace='vra'::regnamespace AND a.grantee=0 AND a.privilege_type='EXECUTE'"));
            assertEquals("0", f.value("SELECT count(*) FROM pg_class WHERE relnamespace='vra'::regnamespace AND relrowsecurity"));
            assertEquals("0", f.value("SELECT count(*) FROM pg_proc WHERE pronamespace='vra'::regnamespace AND (prosrc ~* 'EXECUTE[[:space:]]' OR prosrc ~* 'SET[[:space:]]+ROLE')"));
            assertHistoricalFiles();
            System.out.println("Fresh V1->V4, validate/rerun; existing 7 async tables, 22 functions, 9 triggers (4 deferred): PASS");
        }
    }

    @Test
    void actualVersionTwoUpgradePreservesExistingRowsAndEmitsNoHistoricalEvent() throws Exception {
        try (Fixture f = new Fixture(false)) {
            Flyway v2 = Flyway.configure().dataSource(new OwnerRoleDataSource(f.db.getJdbcUrl(), "vra_migrator", PASSWORD))
                    .locations("classpath:db/migration").schemas("vra").defaultSchema("vra").createSchemas(false).target("2").load();
            assertEquals(2, v2.migrate().migrationsExecuted);
            String reservation = f.reservation();
            f.sql(OWNER, "INSERT INTO vra.inventory_reservation_idempotency VALUES ('actor','success',1,repeat('a',64),'SUCCEEDED','" + reservation + "',1,NULL,now(),now())");
            f.sql(OWNER, "INSERT INTO vra.inventory_reservation_idempotency VALUES ('actor','rejected',1,repeat('b',64),'REJECTED',NULL,NULL,'INSUFFICIENT_STOCK',now(),now())");
            List<String> before = f.businessSnapshot();
            List<String> checksums = f.column("SELECT version||':'||checksum FROM vra.flyway_schema_history ORDER BY installed_rank");
            assertEquals(2, new MigrationRunner().migrate(f.db.getJdbcUrl(), "vra_migrator", PASSWORD));
            assertEquals(before, f.businessSnapshot());
            assertEquals(checksums, f.column("SELECT version||':'||checksum FROM vra.flyway_schema_history WHERE version IN ('1','2') ORDER BY installed_rank"));
            assertEquals("0", f.value("SELECT count(*) FROM vra.outbox_event"));
            assertEquals("0", f.value("SELECT count(*) FROM vra.outbox_delivery"));
            new MigrationRunner().validate(f.db.getJdbcUrl(), "vra_migrator", PASSWORD);
            assertEquals(0, new MigrationRunner().migrate(f.db.getJdbcUrl(), "vra_migrator", PASSWORD));
            assertHistoricalFiles();
            System.out.println("Actual V2->V4: inventory/reservation/success+rejection idempotency unchanged; V1/V2 Flyway checksums " + checksums + "; no historical event");
        }
    }

    @Test
    void exactMembershipOwnershipAndExecutorSteadyStateDenyEscalation() throws Exception {
        try (Fixture f = fresh()) {
            assertEquals(List.of("vra_migrator->vra_owner:true:false:false",
                            "vra_owner->vra_async_executor:true:false:false",
                            "vra_owner->vra_security_executor:true:false:false",
                            "vra_owner->vra_sync_executor:true:false:false",
                            "vra_owner->vra_telemetry_executor:true:false:false"),
                    f.column("SELECT member.rolname||'->'||parent.rolname||':'||m.set_option||':'||m.inherit_option||':'||m.admin_option FROM pg_auth_members m JOIN pg_roles member ON member.oid=m.member JOIN pg_roles parent ON parent.oid=m.roleid WHERE member.rolname LIKE 'vra_%' OR parent.rolname LIKE 'vra_%' ORDER BY member.rolname,parent.rolname"));
            assertEquals("t", f.value("SELECT pg_has_role('vra_migrator','vra_owner','SET') AND pg_has_role('vra_migrator','vra_async_executor','SET')"));
            assertEquals("f", f.value("SELECT rolcanlogin OR rolinherit OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname='vra_async_executor'"));
            assertEquals("f", f.value("SELECT rolsuper OR rolcreaterole FROM pg_roles WHERE rolname='vra_migrator'"));
            assertEquals("0", f.value("SELECT count(*) FROM pg_namespace WHERE nspowner='vra_async_executor'::regrole"));
            assertEquals("0", f.value("SELECT count(*) FROM pg_class WHERE relowner='vra_async_executor'::regrole"));
            assertEquals(OWNER, f.value("SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='vra'"));
            assertEquals("f", f.value("SELECT has_schema_privilege('vra_async_executor','vra','CREATE')"));
            for (String role : ORDINARY) {
                assertEquals("f", f.value("SELECT pg_has_role('" + role + "','vra_owner','SET') OR pg_has_role('" + role + "','vra_async_executor','SET')"));
                assertEquals("t", f.value("SELECT rolcanlogin AND NOT rolinherit AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole FROM pg_roles WHERE rolname='" + role + "'"));
                f.denied(role, "SET ROLE vra_owner", "42501");
                f.denied(role, "SET ROLE vra_async_executor", "42501");
            }
            f.denied(EXECUTOR, "CREATE TABLE vra.forbidden(id int)", "42501");
            f.denied(EXECUTOR, "CREATE SCHEMA forbidden", "42501");
            f.denied(EXECUTOR, "ALTER TABLE vra.outbox_delivery ADD COLUMN forbidden int", "42501");
            f.denied("vra_migrator", "CREATE ROLE forbidden", "42501");
            f.denied("vra_migrator", "CREATE TABLE vra.forbidden(id int)", "42501");
            // Local container trust authentication reaches the NOLOGIN role check directly.
            var login = f.db.execInContainer("psql", "-U", EXECUTOR, "-d", "vra_poc01", "-v", "VERBOSITY=verbose", "-c", "SELECT current_user");
            assertNotEquals(0, login.getExitCode());
            assertTrue(login.getStderr().contains("not permitted to log in"), login.getStderr());
            System.out.println("Capability LOGIN denied: " + login.getStderr().strip());
            for (String role : List.of(RUNTIME, WORKER)) {
                assertThrows(FlywayException.class, () -> new MigrationRunner().migrate(f.db.getJdbcUrl(), role, PASSWORD));
            }
        }
    }

    @Test
    void exactDirectAclAndFunctionCallerMatrixExecuteRealDeniedSql() throws Exception {
        try (Fixture f = fresh()) {
            f.publish(A);
            Map<String, Set<String>> reads = Map.of(
                    RUNTIME, Set.of(), WORKER, Set.of("outbox_event","outbox_delivery","outbox_delivery_history","consumer_inbox","reservation_projection"),
                    RECONCILER, Set.of("outbox_event","outbox_delivery","reconciliation_case","reconciliation_history"),
                    OPERATOR, Set.of("outbox_event","outbox_delivery","reconciliation_case","outbox_delivery_history","reconciliation_history"),
                    REBUILDER, Set.of("reservation_projection"), OBSERVER, Set.of("outbox_delivery","reconciliation_case","outbox_delivery_history","reconciliation_history"),
                    EXECUTOR, Set.of("outbox_event","outbox_delivery","reconciliation_case","reservation_projection","outbox_delivery_history","reconciliation_history"));
            for (String role : reads.keySet()) {
                for (String table : TABLES) {
                    boolean insert = role.equals(WORKER) && Set.of("consumer_inbox","reservation_projection").contains(table)
                            || role.equals(EXECUTOR) && !table.equals("consumer_inbox");
                    boolean update = role.equals(EXECUTOR) && Set.of("outbox_delivery","reconciliation_case","reservation_projection").contains(table);
                    for (String privilege : List.of("SELECT","INSERT","UPDATE","DELETE","TRUNCATE","REFERENCES","TRIGGER","MAINTAIN")) {
                        boolean allowed = switch (privilege) { case "SELECT" -> reads.get(role).contains(table); case "INSERT" -> insert; case "UPDATE" -> update; default -> false; };
                        assertEquals(allowed ? "t" : "f", f.value("SELECT has_table_privilege('"+role+"','vra."+table+"','"+privilege+"')"), role+" "+table+" "+privilege);
                        assertEquals("f", f.value("SELECT has_table_privilege('"+role+"','vra."+table+"','"+privilege+" WITH GRANT OPTION')"));
                    }
                    if (reads.get(role).contains(table)) f.rows(role, "SELECT * FROM vra."+table);
                    else f.denied(role, "SELECT * FROM vra."+table, "42501");
                    if (!insert) f.denied(role, "INSERT INTO vra."+table+" DEFAULT VALUES", "42501");
                    if (!update) f.denied(role, "UPDATE vra."+table+" SET "+(table.equals("reconciliation_history")?"case_id=case_id":table.equals("reservation_projection")?"reservation_id=reservation_id":"event_id=event_id"), "42501");
                    f.denied(role, "DELETE FROM vra."+table, "42501");
                    f.denied(role, "TRUNCATE vra."+table+" CASCADE", "42501");
                }
                f.denied(role, "CREATE TABLE vra.forbidden(id int)", "42501");
                f.denied(role, "ALTER TABLE vra.outbox_delivery ADD COLUMN forbidden int", "42501");
                f.denied(role, "DROP TABLE vra.consumer_inbox", "42501");
                // PostgreSQL can report ineffective GRANT as a warning instead of an error.
                try (Connection connection = f.connection(role); Statement s = connection.createStatement()) {
                    s.execute("GRANT SELECT ON vra.outbox_delivery TO vra_runtime");
                    assertNotNull(s.getWarnings(), "non-owner GRANT must warn");
                    assertEquals("01007", s.getWarnings().getSQLState());
                    System.out.println(role+" GRANT denied by warning SQLSTATE="+s.getWarnings().getSQLState());
                } catch (SQLException denied) { assertEquals("42501", denied.getSQLState()); }
                assertEquals("f", f.value("SELECT has_table_privilege('vra_runtime','vra.outbox_delivery','SELECT')"));
            }
            for (String role : ORDINARY) {
                for (String name : CALLERS.keySet()) {
                    assertEquals(role.equals(CALLERS.get(name)) ? "t" : "f", f.value("SELECT has_function_privilege('"+role+"',p.oid,'EXECUTE') FROM pg_proc p WHERE p.pronamespace='vra'::regnamespace AND p.proname='"+name+"'"));
                    if (!role.equals(CALLERS.get(name))) f.denied(role,"SELECT * FROM vra."+name+"("+ARGUMENTS.get(name)+")","42501");
                }
            }
            for (String role : List.of(WORKER, RECONCILER)) {
                f.denied(role,"UPDATE vra.outbox_delivery SET cycle_claim_limit=16","42501");
                f.denied(role,"UPDATE vra.reconciliation_case SET cycle_claim_limit=16","42501");
                for (String table : List.of("inventory_balance","inventory_reservation","inventory_reservation_idempotency")) {
                    f.denied(role,"DELETE FROM vra."+table,"42501");
                }
                f.denied(role,"UPDATE vra.inventory_balance SET reserved=reserved+1","42501");
                f.denied(role,"UPDATE vra.inventory_reservation_idempotency SET outcome_status=NULL","42501");
                f.denied(role,"INSERT INTO vra.inventory_reservation DEFAULT VALUES","42501");
            }
            f.sql("postgres", "CREATE ROLE public_probe LOGIN PASSWORD '"+PASSWORD+"'; GRANT CONNECT ON DATABASE vra_poc01 TO public_probe; GRANT USAGE ON SCHEMA vra TO public_probe");
            for (String name : CALLERS.keySet()) f.denied("public_probe","SELECT * FROM vra."+name+"("+ARGUMENTS.get(name)+")","42501");
            for (String sequence : List.of("outbox_delivery_history_history_id_seq","reconciliation_history_history_id_seq")) {
                assertEquals("t",f.value("SELECT has_sequence_privilege('vra_async_executor','vra."+sequence+"','USAGE')"));
                for (String role : ORDINARY) f.denied(role,"SELECT nextval('vra."+sequence+"')","42501");
            }
            assertEquals("f",f.value("SELECT has_schema_privilege('vra_async_executor','vra','USAGE WITH GRANT OPTION')"));
        }
    }

    @Test
    void intendedDeliveryProducerConsumerAndControlCapabilitiesCommit() throws Exception {
        try (Fixture f = fresh()) {
            String event = f.publish(A);
            String reservation = f.value("SELECT reservation_id FROM vra.outbox_event WHERE event_id='"+event+"'");
            assertEquals("1:0:5",f.value("SELECT automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit FROM vra.outbox_delivery WHERE event_id='"+event+"'"));
            assertEquals("t",f.value("SELECT e.event_type='inventory.reservation.created' AND e.schema_version=1 AND e.event_id<>r.reservation_id AND (e.reservation_id,e.sku_id,e.owner_id,e.location_id,e.stock_status,e.quantity,e.occurred_at)=(r.reservation_id,r.sku_id,r.owner_id,r.location_id,r.stock_status,r.quantity,r.created_at) FROM vra.outbox_event e JOIN vra.inventory_reservation r USING(reservation_id)"));
            String token = f.claim(A);
            f.sql(WORKER,"BEGIN; INSERT INTO vra.consumer_inbox VALUES ('reservation_projection_v1','"+event+"',statement_timestamp()); INSERT INTO vra.reservation_projection SELECT reservation_id,sku_id,owner_id,location_id,stock_status,quantity,statement_timestamp() FROM vra.outbox_event WHERE event_id='"+event+"'; COMMIT");
            assertEquals("t",f.call(WORKER,"async_complete_delivery",q(event)+","+q(token)));
            assertEquals("SUCCEEDED",f.state(event));
            assertEquals("f",f.call(OPERATOR,"async_control_replay",q(event)+",'CONTROLLED_REPLAY','test'"));
            assertEquals("f",f.call(OPERATOR,"async_control_close",q(event)+",'CONTROLLED_CLOSE','test'"));
            assertEquals("1",f.value("SELECT count(*) FROM vra.consumer_inbox"));
            assertEquals(reservation,f.value("SELECT reservation_id FROM vra.reservation_projection"));

            String failed = f.publish(A);
            String old = f.claim(A);
            assertEquals("t",f.call(WORKER,"async_relinquish_delivery",q(failed)+","+q(old)));
            List<String> unchanged = f.snapshot();
            assertEquals("f",f.call(WORKER,"async_complete_delivery",q(failed)+","+q(old)));
            assertEquals("f",f.call(WORKER,"async_fail_delivery",q(failed)+","+q(old)+",'POISON','POISON'"));
            assertNull(f.call(WORKER,"async_retry_delivery",q(failed)+","+q(old)+",'RETRYABLE_TRANSIENT',1::bigint"));
            assertNull(f.call(WORKER,"async_handoff_unknown",q(failed)+","+q(old)+",'UNKNOWN_EXTERNAL_RESULT'"));
            assertEquals(unchanged,f.snapshot());
            String current = f.claim(A);
            assertNotEquals(old,current);
            assertEquals("t",f.call(WORKER,"async_fail_delivery",q(failed)+","+q(current)+",'POISON','UNSUPPORTED_EVENT_CONTRACT'"));
            assertEquals("t",f.call(OPERATOR,"async_control_replay",q(failed)+",'CONTROLLED_REPLAY','inspected'"));
            assertEquals("2:0:5:2",f.value("SELECT automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit||':'||delivery_attempt_count FROM vra.outbox_delivery WHERE event_id='"+failed+"'"));
            current=f.claim(A);
            assertEquals("RETRY_WAIT",f.call(WORKER,"async_retry_delivery",q(failed)+","+q(current)+",'RETRYABLE_TRANSIENT',1::bigint"));
            f.sql(OWNER,"UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-interval '1 second' WHERE event_id='"+failed+"'");
            current=f.claim(A);
            assertEquals("t",f.call(WORKER,"async_fail_delivery",q(failed)+","+q(current)+",'NON_RETRYABLE','NON_RETRYABLE'"));
            assertEquals("t",f.call(OPERATOR,"async_control_close",q(failed)+",'CONTROLLED_CLOSE','inspected'"));
            assertEquals("CLOSED",f.state(failed));
            assertEquals("f",f.call(OPERATOR,"async_control_replay",q(failed)+",'CONTROLLED_REPLAY','test'"));
            assertTrue(Integer.parseInt(f.value("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"+failed+"'"))>=10);
        }
    }

    @Test
    void intendedReconciliationCapabilitiesKeepPairedStatesAtomic() throws Exception {
        try (Fixture f = fresh()) {
            String event=f.publish(B), token=f.claim(B);
            String caseId=f.call(WORKER,"async_handoff_unknown",q(event)+","+q(token)+",'UNKNOWN_EXTERNAL_RESULT'");
            assertNotNull(caseId);
            assertEquals("1:0:4",f.value("SELECT automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit FROM vra.reconciliation_case WHERE case_id='"+caseId+"'"));
            String caseToken=f.checkClaim();
            assertEquals("WAITING",f.call(RECONCILER,"async_wait_reconciliation",q(caseId)+","+q(caseToken)+",'UNKNOWN_EXTERNAL_RESULT',1::bigint"));
            f.sql(OWNER,"UPDATE vra.reconciliation_case SET next_eligible_at=statement_timestamp()-interval '1 second' WHERE case_id='"+caseId+"'");
            caseToken=f.checkClaim();
            assertEquals("t",f.call(RECONCILER,"async_confirm_external_success",q(caseId)+","+q(caseToken)));
            assertEquals("SUCCEEDED",f.state(event));
            assertEquals("RESOLVED:CONFIRMED_SUCCEEDED",f.value("SELECT state||':'||external_knowledge FROM vra.reconciliation_case WHERE case_id='"+caseId+"'"));
            assertEquals("f",f.call(RECONCILER,"async_confirm_external_success",q(caseId)+","+q(caseToken)));

            event=f.publish(B); token=f.claim(B);
            caseId=f.call(WORKER,"async_handoff_unknown",q(event)+","+q(token)+",'UNKNOWN_EXTERNAL_RESULT'");
            caseToken=f.checkClaim();
            assertEquals("RETRY_WAIT",f.call(RECONCILER,"async_confirm_external_no_effect",q(caseId)+","+q(caseToken)+",true,1::bigint"));
            assertEquals("1:1:5",f.value("SELECT automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit FROM vra.outbox_delivery WHERE event_id='"+event+"'"));
            f.sql(OWNER,"UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-interval '1 second' WHERE event_id='"+event+"'");
            token=f.claim(B);
            String nextCase=f.call(WORKER,"async_handoff_unknown",q(event)+","+q(token)+",'UNKNOWN_EXTERNAL_RESULT'");
            assertNotEquals(caseId,nextCase);
            caseToken=f.checkClaim();
            assertEquals("t",f.call(RECONCILER,"async_exhaust_reconciliation",q(nextCase)+","+q(caseToken)+",'RECONCILIATION_EXHAUSTED'"));
            assertEquals("FAILED",f.state(event));
            assertEquals("f",f.call(OPERATOR,"async_control_replay",q(event)+",'CONTROLLED_REPLAY','test'"));
            assertEquals("t",f.call(OPERATOR,"async_control_resume",q(nextCase)+",'CONTROLLED_RESUME','inspected'"));
            assertEquals("PENDING:UNKNOWN:2:0:4:1",f.value("SELECT state||':'||external_knowledge||':'||automatic_cycle||':'||cycle_claim_count||':'||cycle_claim_limit||':'||lifetime_attempt_count FROM vra.reconciliation_case WHERE case_id='"+nextCase+"'"));
            caseToken=f.checkClaim();
            assertEquals("t",f.call(RECONCILER,"async_exhaust_reconciliation",q(nextCase)+","+q(caseToken)+",'OPERATOR_REQUIRED'"));
            assertEquals("t",f.call(OPERATOR,"async_control_close",q(event)+",'CONTROLLED_CLOSE','inspected'"));
            assertEquals("CLOSED:UNKNOWN",f.value("SELECT state||':'||external_knowledge FROM vra.reconciliation_case WHERE case_id='"+nextCase+"'"));
            assertEquals("CLOSED",f.state(event));
            assertEquals("f",f.call(OPERATOR,"async_control_resume",q(nextCase)+",'CONTROLLED_RESUME','test'"));
        }
    }

    @Test
    void deliveryBudgetAllowsFiveClaimsThenExactlyOneNonDispatchingRecovery() throws Exception {
        try (Fixture f=fresh()) {
            for (String target : List.of(A,B)) {
                String event=f.publish(target);
                for (int count=1;count<=5;count++) {
                    List<List<String>> rows=f.rows(WORKER,"SELECT * FROM vra.async_claim_delivery('"+target+"','test',1,30000::bigint)");
                    assertEquals(1,rows.size());
                    assertEquals(Integer.toString(count),rows.getFirst().get(6));
                    assertEquals("5",rows.getFirst().get(7));
                    assertEquals("t",f.call(WORKER,"async_relinquish_delivery",q(event)+","+q(rows.getFirst().get(1))));
                }
                assertTrue(f.rows(WORKER,"SELECT * FROM vra.async_claim_delivery('"+target+"','test',1,30000::bigint)").isEmpty());
                assertEquals(target.equals(A)?"FAILED":"RECONCILIATION_REQUIRED",f.state(event));
                assertEquals("6:5:6",f.value("SELECT cycle_claim_count||':'||cycle_claim_limit||':'||delivery_attempt_count FROM vra.outbox_delivery WHERE event_id='"+event+"'"));
                assertEquals("1",f.value("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"+event+"' AND action_code LIKE 'RECOVERY_ONLY_%'"));
                List<String> before=f.snapshot();
                assertTrue(f.rows(WORKER,"SELECT * FROM vra.async_claim_delivery('"+target+"','test',1,30000::bigint)").isEmpty());
                assertEquals(before,f.snapshot());
                if (target.equals(B)) {
                    String caseId=f.value("SELECT case_id FROM vra.reconciliation_case WHERE event_id='"+event+"'");
                    String token=f.checkClaim();
                    assertEquals("t",f.call(RECONCILER,"async_confirm_external_success",q(caseId)+","+q(token)));
                    assertEquals("SUCCEEDED",f.state(event));
                    assertEquals("6",f.value("SELECT cycle_claim_count FROM vra.outbox_delivery WHERE event_id='"+event+"'"));
                }
            }
        }
    }

    @Test
    void reconciliationBudgetAllowsFourClaimsThenExactlyOneNonQueryableRecovery() throws Exception {
        try (Fixture f=fresh()) {
            String event=f.publish(B), deliveryToken=f.claim(B);
            String caseId=f.call(WORKER,"async_handoff_unknown",q(event)+","+q(deliveryToken)+",'UNKNOWN_EXTERNAL_RESULT'");
            String last=null;
            for (int count=1;count<=4;count++) {
                List<List<String>> rows=f.rows(RECONCILER,"SELECT * FROM vra.async_claim_reconciliation('test',1,30000::bigint)");
                assertEquals(1,rows.size());
                assertEquals(Integer.toString(count),rows.getFirst().get(5));
                assertEquals("4",rows.getFirst().get(6));
                last=rows.getFirst().get(2);
                f.sql(OWNER,"UPDATE vra.reconciliation_case SET claim_until=statement_timestamp()-interval '1 second' WHERE case_id='"+caseId+"'");
            }
            assertTrue(f.rows(RECONCILER,"SELECT * FROM vra.async_claim_reconciliation('test',1,30000::bigint)").isEmpty());
            assertEquals("OPERATOR_REQUIRED:UNKNOWN:5:4:5",f.value("SELECT state||':'||external_knowledge||':'||cycle_claim_count||':'||cycle_claim_limit||':'||lifetime_attempt_count FROM vra.reconciliation_case WHERE case_id='"+caseId+"'"));
            assertEquals("FAILED",f.state(event));
            assertEquals("1",f.value("SELECT count(*) FROM vra.reconciliation_history WHERE case_id='"+caseId+"' AND action_code='RECOVERY_ONLY_EXHAUSTED'"));
            List<String> before=f.snapshot();
            assertTrue(f.rows(RECONCILER,"SELECT * FROM vra.async_claim_reconciliation('test',1,30000::bigint)").isEmpty());
            assertEquals("f",f.call(RECONCILER,"async_confirm_external_success",q(caseId)+","+q(last)));
            assertEquals(before,f.snapshot());
            assertEquals("t",f.call(OPERATOR,"async_control_close",q(event)+",'CONTROLLED_CLOSE','inspected'"));
            assertEquals("CLOSED:5",f.value("SELECT state||':'||cycle_claim_count FROM vra.reconciliation_case WHERE case_id='"+caseId+"'"));
        }
    }

    @Test
    void normalRetryAndObservationExhaustionNeverScheduleBeyondDurableLimits() throws Exception {
        try (Fixture f=fresh()) {
            String event=f.publish(A);
            for (int count=1;count<=5;count++) {
                String token=f.claim(A);
                assertEquals(count<5?"RETRY_WAIT":"FAILED",f.call(WORKER,"async_retry_delivery",q(event)+","+q(token)+",'RETRYABLE_TRANSIENT',1::bigint"));
                if(count<5) f.sql(OWNER,"UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-interval '1 second' WHERE event_id='"+event+"'");
            }
            assertEquals("5",f.value("SELECT cycle_claim_count FROM vra.outbox_delivery WHERE event_id='"+event+"'"));
            event=f.publish(B);
            String token=f.claim(B);
            String caseId=f.call(WORKER,"async_handoff_unknown",q(event)+","+q(token)+",'UNKNOWN_EXTERNAL_RESULT'");
            for (int count=1;count<=4;count++) {
                token=f.checkClaim();
                assertEquals(count<4?"WAITING":"OPERATOR_REQUIRED",f.call(RECONCILER,"async_wait_reconciliation",q(caseId)+","+q(token)+",'UNKNOWN_EXTERNAL_RESULT',1::bigint"));
                if(count<4) f.sql(OWNER,"UPDATE vra.reconciliation_case SET next_eligible_at=statement_timestamp()-interval '1 second' WHERE case_id='"+caseId+"'");
            }
            assertEquals("FAILED",f.state(event));
            assertEquals("4",f.value("SELECT cycle_claim_count FROM vra.reconciliation_case WHERE case_id='"+caseId+"'"));
        }
    }

    @Test
    void staticShapesIdentityForeignKeysAndUniquenessRejectMalformedRows() throws Exception {
        try (Fixture f=fresh()) {
            String event=f.publish(B);
            for (String assignment : List.of("state='INVALID'","failure_class='INVALID'","reason_code='raw secret text'",
                    "cycle_claim_limit=0","cycle_claim_limit=17","cycle_claim_count=-1","automatic_cycle=0",
                    "delivery_attempt_count=-1","cycle_claim_count=7,delivery_attempt_count=7",
                    "state='PROCESSING'","state='FAILED'","state='RETRY_WAIT'","state='RECONCILIATION_REQUIRED'",
                    "claim_relinquished=true","claim_token=gen_random_uuid()","target_code='INVALID'",
                    "state='PROCESSING',claim_token=gen_random_uuid(),claim_until=now(),cycle_claim_count=6,delivery_attempt_count=6")) {
                f.denied(OWNER,"UPDATE vra.outbox_delivery SET "+assignment+" WHERE event_id='"+event+"'","23514");
            }
            f.denied(OWNER,"UPDATE vra.outbox_delivery SET target_code='"+A+"' WHERE event_id='"+event+"'","23514");
            f.denied(OWNER,"UPDATE vra.outbox_delivery SET created_at=created_at+interval '1 second' WHERE event_id='"+event+"'","23514");
            String token=f.claim(B);
            String caseId=f.call(WORKER,"async_handoff_unknown",q(event)+","+q(token)+",'UNKNOWN_EXTERNAL_RESULT'");
            for (String assignment : List.of("state='INVALID'","external_knowledge='INVALID'","reason_code='bad reason'",
                    "cycle_claim_limit=0","cycle_claim_limit=17","cycle_claim_count=-1","automatic_cycle=0",
                    "lifetime_attempt_count=-1","cycle_claim_count=6,lifetime_attempt_count=6",
                    "state='CHECKING'","state='RESOLVED'","external_knowledge='CONFIRMED_SUCCEEDED'",
                    "claim_token=gen_random_uuid()","next_eligible_at=NULL",
                    "state='CHECKING',next_eligible_at=NULL,claim_token=gen_random_uuid(),claim_until=now(),cycle_claim_count=5,lifetime_attempt_count=5",
                    "case_id=gen_random_uuid()","created_at=created_at+interval '1 second'")) {
                f.denied(OWNER,"UPDATE vra.reconciliation_case SET "+assignment+" WHERE case_id='"+caseId+"'","23514");
            }
            f.denied(OWNER,"INSERT INTO vra.reconciliation_case SELECT gen_random_uuid(),event_id,state,external_knowledge,reason_code,next_eligible_at,claim_token,claim_until,lifetime_attempt_count,automatic_cycle,cycle_claim_count,cycle_claim_limit,created_at,state_changed_at FROM vra.reconciliation_case WHERE case_id='"+caseId+"'","23505");
            f.denied(OWNER,"INSERT INTO vra.outbox_delivery(event_id,target_code,state,cycle_claim_limit,created_at,state_changed_at) VALUES (gen_random_uuid(),'"+A+"','READY',5,now(),now())","23503");
            f.denied(OWNER,"INSERT INTO vra.reservation_projection VALUES (gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'AVAILABLE',1,now())","23503");
            String reservation=f.value("SELECT reservation_id FROM vra.outbox_event WHERE event_id='"+event+"'");
            f.denied(RUNTIME,"SELECT vra.async_publish_reservation(gen_random_uuid(),"+q(reservation)+",'"+B+"')","23505");
            f.sql(WORKER,"INSERT INTO vra.consumer_inbox VALUES ('test',gen_random_uuid(),now())");
            f.denied(WORKER,"INSERT INTO vra.consumer_inbox SELECT * FROM vra.consumer_inbox","23505");
            f.denied(WORKER,"INSERT INTO vra.consumer_inbox VALUES (' ',gen_random_uuid(),now())","23514");
            for (String table : List.of("outbox_delivery_history","reconciliation_history")) {
                String key=table.equals("outbox_delivery_history")?"event_id":"case_id";
                String id=table.equals("outbox_delivery_history")?event:caseId;
                String state=table.equals("outbox_delivery_history")?"READY":"PENDING";
                for (String values : List.of("'INVALID',0,5,'REASON'","'CREATED',0,0,'REASON'","'CREATED',0,17,'REASON'","'CREATED',7,5,'REASON'","'CREATED',0,5,'unsafe reason'")) {
                    String[] parts=values.split(",");
                    f.denied(OWNER,"INSERT INTO vra."+table+"("+key+",action_code,to_state,lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,recorded_at) VALUES ('"+id+"',"+parts[0]+",'"+state+"',10,1,"+parts[1]+","+parts[2]+","+parts[3]+",'OUTBOX_WORKER',now())","23514");
                }
            }
        }
    }

    @Test
    void deferredPairsAndRecoveryEvidenceRejectAtCommitAndPermitCompoundShapes() throws Exception {
        try (Fixture f=fresh()) {
            String event=f.publish(B);
            f.commitDenied("UPDATE vra.outbox_delivery SET state='RECONCILIATION_REQUIRED',failure_class='UNKNOWN_OUTCOME',reason_code='UNKNOWN_EXTERNAL_RESULT' WHERE event_id='"+event+"'");
            f.commitDenied("UPDATE vra.outbox_delivery SET state='FAILED',failure_class='OPERATOR_REQUIRED',reason_code='RETRY_EXHAUSTED',cycle_claim_count=6,delivery_attempt_count=6 WHERE event_id='"+event+"'");
            String caseId=UUID.randomUUID().toString();
            String insert="INSERT INTO vra.reconciliation_case(case_id,event_id,state,external_knowledge,reason_code,next_eligible_at,cycle_claim_limit,created_at,state_changed_at) VALUES ('"+caseId+"','"+event+"','PENDING','UNKNOWN','UNKNOWN_EXTERNAL_RESULT',now(),4,now(),now())";
            f.commitDenied(insert);
            // Each intermediate pair is invalid, but the compound committed shape is valid.
            f.sql(OWNER,"BEGIN; "+insert+"; UPDATE vra.outbox_delivery SET state='RECONCILIATION_REQUIRED',failure_class='UNKNOWN_OUTCOME',reason_code='UNKNOWN_EXTERNAL_RESULT' WHERE event_id='"+event+"'; COMMIT");
            assertEquals("RECONCILIATION_REQUIRED",f.state(event));
            f.commitDenied("UPDATE vra.outbox_delivery SET state='SUCCEEDED',failure_class=NULL,reason_code=NULL WHERE event_id='"+event+"'");
            f.commitDenied("UPDATE vra.reconciliation_case SET state='OPERATOR_REQUIRED',next_eligible_at=NULL WHERE case_id='"+caseId+"'");
            f.commitDenied("UPDATE vra.outbox_delivery SET state='FAILED' WHERE event_id='"+event+"'; UPDATE vra.reconciliation_case SET state='OPERATOR_REQUIRED',next_eligible_at=NULL,cycle_claim_count=5,lifetime_attempt_count=5 WHERE case_id='"+caseId+"'");
            assertEquals("PENDING",f.value("SELECT state FROM vra.reconciliation_case WHERE case_id='"+caseId+"'"));
            assertEquals("t",f.value("SELECT bool_and(tgdeferrable AND tginitdeferred) FROM pg_trigger WHERE tgname IN ('outbox_delivery_pair','reconciliation_case_pair','outbox_delivery_recovery','reconciliation_case_recovery')"));
        }
    }

    @Test
    void immutableEvidenceRejectsOwnerRewriteAndFailedHistoryAppendRollsBackWholeOperation() throws Exception {
        try (Fixture f=fresh()) {
            String event=f.publish(B), token=f.claim(B);
            // Required history INSERT fails after the function's current-row update/case INSERT.
            f.sql(OWNER,"ALTER TABLE vra.reconciliation_history ADD CONSTRAINT test_reject_history CHECK (false) NOT VALID");
            f.denied(WORKER,"SELECT vra.async_handoff_unknown("+q(event)+","+q(token)+",'UNKNOWN_EXTERNAL_RESULT')","23514");
            assertEquals("PROCESSING",f.state(event));
            assertEquals("0",f.value("SELECT count(*) FROM vra.reconciliation_case"));
            f.sql(OWNER,"ALTER TABLE vra.reconciliation_history DROP CONSTRAINT test_reject_history");
            String caseId=f.call(WORKER,"async_handoff_unknown",q(event)+","+q(token)+",'UNKNOWN_EXTERNAL_RESULT'");
            String caseToken=f.checkClaim();
            f.sql(OWNER,"ALTER TABLE vra.outbox_delivery_history ADD CONSTRAINT test_reject_history CHECK (false) NOT VALID");
            f.denied(RECONCILER,"SELECT vra.async_confirm_external_success("+q(caseId)+","+q(caseToken)+")","23514");
            assertEquals("RECONCILIATION_REQUIRED",f.state(event));
            assertEquals("CHECKING",f.value("SELECT state FROM vra.reconciliation_case WHERE case_id='"+caseId+"'"));
            f.sql(OWNER,"ALTER TABLE vra.outbox_delivery_history DROP CONSTRAINT test_reject_history");
            for (String table : List.of("outbox_event","outbox_delivery_history","reconciliation_history")) {
                String col=table.equals("reconciliation_history")?"case_id":"event_id";
                f.denied(OWNER,"UPDATE vra."+table+" SET "+col+"="+col,"23514");
                f.denied(OWNER,"DELETE FROM vra."+table,"23514");
                f.denied(WORKER,"UPDATE vra."+table+" SET "+col+"="+col,"42501");
                f.denied(WORKER,"DELETE FROM vra."+table,"42501");
            }
            assertEquals("t",f.call(RECONCILER,"async_confirm_external_success",q(caseId)+","+q(caseToken)));
        }
    }

    @Test
    void invalidArgumentsCannotMutateAndDedicatedRebuildRepairsFromReservations() throws Exception {
        try (Fixture f=fresh()) {
            String event=f.publish(A);
            for (String args : List.of("NULL,'test',1,1::bigint","'INVALID','test',1,1::bigint",
                    "'"+A+"',NULL,1,1::bigint","'"+A+"',' ',1,1::bigint","'"+A+"',repeat('x',129)::varchar,1,1::bigint",
                    "'"+A+"','test',0,1::bigint","'"+A+"','test',129,1::bigint","'"+A+"','test',1,0::bigint",
                    "'"+A+"','test',1,86400001::bigint")) {
                f.denied(WORKER,"SELECT * FROM vra.async_claim_delivery("+args+")","22023");
            }
            f.denied(RUNTIME,"SELECT vra.async_publish_reservation(NULL,"+ID+",'"+A+"')","22023");
            f.denied(RUNTIME,"SELECT vra.async_publish_reservation("+ID+","+ID+",'"+A+"')","22023");
            f.denied(WORKER,"SELECT vra.async_complete_delivery(NULL,"+ID+")","22023");
            f.denied(WORKER,"SELECT vra.async_retry_delivery("+ID+","+ID+",'RAW_EXCEPTION',1::bigint)","22023");
            f.denied(WORKER,"SELECT vra.async_fail_delivery("+ID+","+ID+",'UNKNOWN_OUTCOME','UNKNOWN_EXTERNAL_RESULT')","22023");
            f.denied(RECONCILER,"SELECT * FROM vra.async_claim_reconciliation('test',0,1::bigint)","22023");
            f.denied(RECONCILER,"SELECT vra.async_confirm_external_no_effect("+ID+","+ID+",NULL,1::bigint)","22023");
            f.denied(OPERATOR,"SELECT vra.async_control_replay("+ID+",'CONTROLLED_REPLAY',' ')","22023");
            f.denied(WORKER,"SELECT * FROM vra.async_claim_delivery('"+A+"','test',1,1::bigint,16)","42883");
            f.denied(RECONCILER,"SELECT * FROM vra.async_claim_reconciliation('test',1,1::bigint,16)","42883");
            List<String> business=f.businessSnapshot();
            f.sql(WORKER,"INSERT INTO vra.consumer_inbox VALUES ('reservation_projection_v1','"+event+"',now())");
            assertEquals(List.of(List.of("1","0")),f.rows(REBUILDER,"SELECT * FROM vra.async_rebuild_reservation_projection()"));
            f.sql(OWNER,"UPDATE vra.reservation_projection SET quantity=99");
            assertEquals(List.of(List.of("0","1")),f.rows(REBUILDER,"SELECT * FROM vra.async_rebuild_reservation_projection()"));
            assertEquals("0",f.value("SELECT count(*) FROM (SELECT reservation_id,sku_id,owner_id,location_id,stock_status,quantity FROM vra.inventory_reservation EXCEPT SELECT reservation_id,sku_id,owner_id,location_id,stock_status,quantity FROM vra.reservation_projection) difference"));
            assertEquals(business,f.businessSnapshot());
            assertEquals("1",f.value("SELECT count(*) FROM vra.consumer_inbox"));
            f.rows(REBUILDER,"SELECT * FROM vra.inventory_reservation");
        }
    }

    private static String q(String uuid) { return "'"+UUID.fromString(uuid)+"'::uuid"; }

    private static Fixture fresh() throws Exception { return new Fixture(true); }

    private static Path root() {
        Path root=Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
            root=root.getParent();
            assertNotNull(root,"repository root");
        }
        return root;
    }

    private static void assertHistoricalFiles() throws Exception {
        for (var entry : Map.of(
                "V1__inventory_reservation_foundation.sql","d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4",
                "V2__inventory_reservation_idempotency.sql","7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb").entrySet()) {
            byte[] bytes=Files.readAllBytes(root().resolve("backend/migration/src/main/resources/db/migration/"+entry.getKey()));
            assertEquals(entry.getValue(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),entry.getKey());
        }
    }

    static void bootstrapAsync(PostgreSQLContainer postgres) throws Exception {
        runBootstrap(postgres,"validation/poc-03/db/bootstrap-async-roles.sql");
        runBootstrap(postgres,"validation/poc-04/db/bootstrap-security-roles.sql");
    }

    private static void runBootstrap(PostgreSQLContainer postgres,String script) throws Exception {
        assertTrue(Files.isRegularFile(root().resolve(script)),"bootstrap exists: "+script);
        postgres.copyFileToContainer(MountableFile.forHostPath(root().resolve(script)),"/tmp/bootstrap.sql");
        var result=postgres.execInContainer("psql","-U","postgres","-d","vra_poc01",
                "-v","ON_ERROR_STOP=1","-v","migrator_password="+PASSWORD,"-v","runtime_password="+PASSWORD,
                "-v","outbox_worker_password="+PASSWORD,"-v","reconciliation_worker_password="+PASSWORD,
                "-v","async_operator_password="+PASSWORD,"-v","projection_rebuilder_password="+PASSWORD,
                "-v","async_observer_password="+PASSWORD,"-f","/tmp/bootstrap.sql");
        assertEquals(0,result.getExitCode(),result.getStderr());
    }

    private static final class Fixture implements AutoCloseable {
        final PostgreSQLContainer db=new dev.vra.poc04.external.RunOwnedPostgreSQLContainer("postgres:17.11").withDatabaseName("vra_poc01")
                .withUsername("postgres").withPassword(PASSWORD);

        Fixture(boolean migrate) throws Exception {
            db.start();
            try {
                runBootstrap(db,"validation/poc-01/db/bootstrap.sql");
                bootstrapAsync(db);
                if(migrate) assertEquals(4,new MigrationRunner().migrate(db.getJdbcUrl(),"vra_migrator",PASSWORD));
            } catch (Throwable failure) { db.stop(); throw failure; }
        }
        Connection connection(String role) throws SQLException {
            if(role.equals(OWNER)||role.equals(EXECUTOR)) {
                Connection c=new OwnerRoleDataSource(db.getJdbcUrl(),"vra_migrator",PASSWORD).getConnection();
                if(role.equals(EXECUTOR)) { try(Statement s=c.createStatement()) { s.execute("SET ROLE vra_async_executor"); } }
                return c;
            }
            return DriverManager.getConnection(db.getJdbcUrl(),role,PASSWORD);
        }
        void sql(String role,String sql) throws SQLException {
            try(Connection c=connection(role);Statement s=c.createStatement()) { s.execute(sql); }
        }
        List<List<String>> rows(String role,String sql) throws SQLException {
            try(Connection c=connection(role);Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)) {
                List<List<String>> rows=new ArrayList<>();
                while(r.next()) {
                    List<String> row=new ArrayList<>();
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.add(r.getString(i));
                    rows.add(row);
                }
                return rows;
            }
        }
        String value(String sql) throws SQLException { return rows("postgres",sql).getFirst().getFirst(); }
        List<String> column(String sql) throws SQLException { return rows("postgres",sql).stream().map(List::getFirst).toList(); }
        String call(String role,String function,String args) throws SQLException {
            return rows(role,"SELECT vra."+function+"("+args+")").getFirst().getFirst();
        }
        String reservation() throws SQLException {
            String sku=UUID.randomUUID().toString(),owner=UUID.randomUUID().toString(),location=UUID.randomUUID().toString(),reservation=UUID.randomUUID().toString();
            sql(OWNER,"INSERT INTO vra.inventory_balance VALUES ('"+sku+"','"+owner+"','"+location+"','AVAILABLE',10,1,1)");
            sql(RUNTIME,"INSERT INTO vra.inventory_reservation VALUES ('"+reservation+"','"+sku+"','"+owner+"','"+location+"','AVAILABLE',1,'2026-09-27T00:00:00Z')");
            return reservation;
        }
        String publish(String target) throws SQLException {
            String reservation=reservation(),event=UUID.randomUUID().toString();
            assertEquals(event,call(RUNTIME,"async_publish_reservation",q(event)+","+q(reservation)+",'"+target+"'"));
            return event;
        }
        String claim(String target) throws SQLException {
            List<List<String>> claims=rows(WORKER,"SELECT * FROM vra.async_claim_delivery('"+target+"','test',1,30000::bigint)");
            assertEquals(1,claims.size());
            return claims.getFirst().get(1);
        }
        String checkClaim() throws SQLException {
            List<List<String>> claims=rows(RECONCILER,"SELECT * FROM vra.async_claim_reconciliation('test',1,30000::bigint)");
            assertEquals(1,claims.size());
            return claims.getFirst().get(2);
        }
        String state(String event) throws SQLException { return value("SELECT state FROM vra.outbox_delivery WHERE event_id='"+event+"'"); }
        List<String> businessSnapshot() throws SQLException {
            return snapshotTables(List.of("inventory_balance","inventory_reservation","inventory_reservation_idempotency"));
        }
        List<String> snapshot() throws SQLException { return snapshotTables(Arrays.asList(TABLES)); }
        List<String> snapshotTables(List<String> tables) throws SQLException {
            List<String> snapshot=new ArrayList<>();
            try(Connection c=connection("postgres");Statement s=c.createStatement()) {
                for(String table:tables) {
                    try(ResultSet r=s.executeQuery("SELECT row_to_json(t)::text FROM vra."+table+" t ORDER BY row_to_json(t)::text")) {
                        while(r.next()) snapshot.add(table+":"+r.getString(1));
                    }
                }
            }
            return snapshot;
        }
        void denied(String role,String sql,String state) throws Exception {
            List<String> before=snapshot();
            SQLException failure=assertThrows(SQLException.class,()->sql(role,sql),role+" "+sql);
            assertEquals(state,failure.getSQLState(),role+" "+sql+" "+failure.getMessage());
            assertEquals(before,snapshot(),"denial must preserve authoritative async state");
            System.out.println(role+" denied SQLSTATE="+state+" "+sql);
        }
        void commitDenied(String sql) throws Exception {
            List<String> before=snapshot();
            try(Connection c=connection(OWNER);Statement s=c.createStatement()) {
                c.setAutoCommit(false);
                s.execute(sql); // Intermediate shape must be accepted; commit is the failing boundary.
                SQLException failure=assertThrows(SQLException.class,c::commit);
                assertEquals("23514",failure.getSQLState());
                c.rollback();
                System.out.println("Deferred COMMIT rejected SQLSTATE="+failure.getSQLState()+" "+failure.getMessage());
            }
            assertEquals(before,snapshot());
        }
        @Override public void close() { db.stop(); }
    }
}
