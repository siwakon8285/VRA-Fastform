import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** TEST-only evidence adapter; reuses existing compiled Phase-1 fixture and assertions. */
public final class PhaseOneEvidenceCapture {
    static final Class<?> TEST;
    static final Class<?> FIXTURE;
    static final List<String> WORKLOADS = List.of("vra_runtime", "vra_outbox_worker", "vra_reconciliation_worker", "vra_async_operator", "vra_projection_rebuilder", "vra_async_observer", "vra_factor_fixture", "vra_audit_evidence_reader", "vra_security_telemetry_observer");
    static final List<String> EXECUTORS = List.of("vra_sync_executor", "vra_security_executor", "vra_telemetry_executor");
    static final List<String> FOUNDATION;
    static final List<String> PRIOR;
    static final List<String> ALL;
    static {
        try {
            TEST = Class.forName("dev.vra.migration.Poc04FoundationMigrationIntegrationTest");
            FIXTURE = Class.forName("dev.vra.migration.Poc04FoundationMigrationIntegrationTest$Fixture");
            FOUNDATION = (List<String>) field(TEST, null, "FOUNDATION_TABLES");
            PRIOR = (List<String>) field(TEST, null, "PRIOR_TABLES");
            ALL = new ArrayList<>(PRIOR); ALL.addAll(FOUNDATION);
        } catch (Throwable e) { throw new ExceptionInInitializerError(e); }
    }
    static Path destination;
    static final Map<String,Object> document = new LinkedHashMap<>();
    static final List<Object> databases = new ArrayList<>();
    static Map<String,Object> current;
    static String stage;

    public static void main(String[] args) throws Throwable {
        if (args.length != 2 || !Set.of("fresh", "populated", "both").contains(args[1])) throw new IllegalArgumentException("usage: evidence.json fresh|populated|both");
        destination = Path.of(args[0]);
        document.put("adapter", "PhaseOneEvidenceCapture: reuses existing compiled fixture and assertions; no production implementation");
        document.put("started_at", Instant.now().toString());
        document.put("endpoint", System.getenv("DOCKER_HOST"));
        document.put("databases", databases);
        document.put("status", "RUNNING");
        save();
        try {
            if (!args[1].equals("populated")) execute("fresh");
            if (!args[1].equals("fresh")) execute("populated");
            document.put("status", "PASS");
        } catch (Throwable failure) {
            document.put("status", "FAIL");
            document.put("failing_stage", stage);
            document.put("exception_type", failure.getClass().getName());
            if (failure instanceof SQLException sql) document.put("sqlstate", sql.getSQLState());
            // No exception message or JDBC properties are exported: they can include sensitive SQL values.
            save();
            throw new AssertionError("Sanitized evidence adapter failure at stage " + stage + " (" + failure.getClass().getName() + ")");
        } finally {
            document.put("completed_at", Instant.now().toString());
            save();
        }
    }

    static void execute(String mode) throws Throwable {
        current = new LinkedHashMap<>(); databases.add(current);
        current.put("mode", mode); current.put("status", "RUNNING");
        current.put("stages", new ArrayList<>()); save();
        stage = mode + ":fixture_initialization";
        Constructor<?> constructor = FIXTURE.getDeclaredConstructor(); constructor.setAccessible(true);
        Object fixture = constructor.newInstance();
        try {
            current.put("identity", identity(fixture)); save();
            Map<?,?> beforeData = null; Object beforeHistory = null, beforeCatalog = null;
            if (mode.equals("populated")) {
                migrate(fixture, "3", 3); validate(fixture, "3");
                stage = mode + ":seedPriorState"; callStatic("seedPriorState", fixture); passed("seedPriorState");
                beforeData = snapshot(fixture, PRIOR);
                beforeHistory = rows(fixture, HISTORY);
                beforeCatalog = call(fixture, "priorCatalog");
                current.put("prior_before_row_digests", beforeData);
                current.put("prior_before_row_counts", rowCounts(fixture, PRIOR));
                current.put("prior_before_flyway_history", beforeHistory);
                current.put("prior_before_catalog", beforeCatalog); save();
                migrate(fixture, null, 1);
            } else migrate(fixture, null, 4);
            validate(fixture, null);
            if (mode.equals("populated")) {
                var afterData = snapshot(fixture, PRIOR);
                var afterHistory = rows(fixture, HISTORY_PRIOR);
                var afterCatalog = call(fixture, "priorCatalog");
                requireEqual(beforeData, afterData, "prior data retained");
                requireEqual(beforeHistory, afterHistory, "prior history retained");
                requireEqual(beforeCatalog, afterCatalog, "prior catalog retained");
                current.put("prior_after_row_digests", afterData);
                current.put("prior_after_row_counts", rowCounts(fixture, PRIOR));
                current.put("prior_after_flyway_history", afterHistory);
                current.put("prior_after_catalog", afterCatalog); passed("retained_prior_data_history_catalog");
            }
            requireEqual(List.of("1", "2", "3", "4"), call(fixture, "column", "SELECT version FROM vra.flyway_schema_history WHERE success ORDER BY installed_rank"), "history versions");
            requireEqual("1", call(fixture, "value", "SELECT count(*) FROM vra.flyway_schema_history WHERE version='4'"), "V4 applied once");
            requireEqual("22", call(fixture, "value", "SELECT count(*) FROM pg_proc WHERE pronamespace='vra'::regnamespace"), "no Phase-2 functions");
            current.put("flyway_history", rows(fixture, HISTORY));
            var rerunBeforeData = snapshot(fixture, ALL);
            var rerunBeforeHistory = rows(fixture, HISTORY);
            var rerunBeforeCatalog = call(fixture, "catalog");
            current.put("rerun_before_row_digests", rerunBeforeData); save();
            migrate(fixture, null, 0); validate(fixture, null);
            var rerunAfterData = snapshot(fixture, ALL);
            requireEqual(rerunBeforeData, rerunAfterData, "rerun data unchanged");
            requireEqual(rerunBeforeHistory, rows(fixture, HISTORY), "rerun history unchanged");
            requireEqual(rerunBeforeCatalog, call(fixture, "catalog"), "rerun catalog unchanged");
            current.put("rerun_after_row_digests", rerunAfterData); passed("same_database_rerun_unchanged");
            for (String verifier : List.of("verifyRoleBoundary", "verifyFoundationCatalog", "verifyGrantsAndSqlDenials", "verifyDataModelNegatives")) {
                stage = mode + ":" + verifier; callStatic(verifier, fixture); passed(verifier);
            }
            current.put("role_state", call(fixture, "roleState"));
            current.put("transitive_set_graph", rows(fixture, SET_GRAPH));
            current.put("schema_create_holders", rows(fixture, "SELECT rolname FROM pg_roles WHERE has_schema_privilege(oid,'vra','CREATE') ORDER BY rolname"));
            current.put("trusted_schema_acl", rows(fixture, "SELECT nspname,pg_get_userbyid(nspowner),nspacl::text FROM pg_namespace WHERE nspname='vra'"));
            current.put("public_table_sequence_grants", rows(fixture, PUBLIC_REL));
            current.put("public_schema_grants", rows(fixture, PUBLIC_SCHEMA));
            current.put("full_v1_v4_catalog", call(fixture, "catalog"));
            current.put("foundation_row_counts_after_negatives", rowCounts(fixture, FOUNDATION));
            current.put("runtime_and_workload_v4_acl", rows(fixture, ACL));
            save();
            List<Object> denials = new ArrayList<>(); current.put("observed_sql_denials", denials);
            for (String role : WORKLOADS) {
                for (String executor : EXECUTORS) captureDenial(fixture, denials, role, "SET ROLE " + executor, "42501");
                captureDenial(fixture, denials, role, "CREATE TABLE vra.forbidden_evidence_capture(id bigint)", "42501");
                captureDenial(fixture, denials, role, "CREATE ROLE vra_forbidden_evidence_capture LOGIN", "42501");
                captureDenial(fixture, denials, role, "ALTER ROLE vra_security_executor LOGIN", "42501");
            }
            for (String executor : EXECUTORS) captureDenial(fixture, denials, executor, "CREATE TABLE vra.forbidden_evidence_capture(id bigint)", "42501");
            for (var item : List.of(
                    List.of("UPDATE vra.account SET state='INVALID'", "23514"),
                    List.of("UPDATE vra.account SET version=-1", "23514"),
                    List.of("UPDATE vra.account SET auth_generation=-1", "23514"),
                    List.of("UPDATE vra.browser_session SET verifier=decode(repeat('00',31),'hex')", "23514"),
                    List.of("INSERT INTO vra.external_identity_binding SELECT * FROM vra.external_identity_binding", "23505"),
                    List.of("INSERT INTO vra.organization_membership SELECT * FROM vra.organization_membership", "23505"),
                    List.of("INSERT INTO vra.inventory_operation_grant SELECT * FROM vra.inventory_operation_grant", "23505"),
                    List.of("UPDATE vra.external_identity_binding SET account_id=gen_random_uuid()", "23503"),
                    List.of("UPDATE vra.inventory_operation_grant SET operation='INVALID'", "23514"),
                    List.of("UPDATE vra.security_event SET actor_type='HUMAN',actor_account_id=NULL", "23514"),
                    List.of("UPDATE vra.security_event SET source_kind='POSTGRES_ACL' WHERE source_kind='APP'", "23514"),
                    List.of("UPDATE vra.security_event SET source_kind='APP' WHERE source_kind='POSTGRES_ACL'", "23514"))) {
                captureDenial(fixture, denials, "vra_owner", item.get(0), item.get(1));
            }
            captureDenial(fixture, denials, "vra_runtime", "INSERT INTO vra.security_event(source_kind) VALUES ('POSTGRES_ACL')", "42501");
            captureDenial(fixture, denials, "vra_runtime", "INSERT INTO vra.security_event(actor_type,actor_workload,event_code,outcome_code,target_type,risk_code,request_id) VALUES ('WORKLOAD','vra_runtime','AUDIT_TAMPER_ATTEMPT','DENIED_42501','PROTECTED_AUDIT','AUDIT_INTEGRITY_PROBE',gen_random_uuid())", "23514");
            if (beforeData != null) requireEqual(beforeData, snapshot(fixture, PRIOR), "prior rows retained after all negative proofs");
            current.put("prior_final_row_digests", snapshot(fixture, PRIOR));
            current.put("status", "PASS"); save();
        } catch (Throwable failure) {
            current.put("status", "FAIL"); current.put("failing_stage", stage); current.put("exception_type", unwrap(failure).getClass().getName()); save();
            throw unwrap(failure);
        } finally {
            stage = mode + ":owned_resource_teardown";
            call(fixture, "close");
            current.put("teardown", "PASS: existing fixture rechecked physical identity and labels, removed exact owned IDs, asserted NotFound for each recorded container/network/volume");
            save();
        }
    }

    static Map<String,Object> identity(Object fixture) throws Throwable {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String name : List.of("labels", "containerId", "containerName", "containerCreatedAt", "volumeName", "volumeCreatedAt", "volumeMountpoint", "networkId", "networkName", "networkCreatedAt", "declaredEndpoint")) result.put(name, field(FIXTURE, fixture, name));
        result.put("database_identity", rows(fixture, "SELECT current_database(),current_user,version(),current_setting('server_version_num'),system_identifier::text FROM pg_control_system()"));
        Object database = field(FIXTURE, fixture, "database");
        Object inspect = call(database, "getContainerInfo");
        result.put("container_image_id", call(inspect, "getImageId"));
        result.put("container_requested_image", call(call(inspect, "getConfig"), "getImage"));
        List<Object> mounts = new ArrayList<>();
        for (Object mount : (List<?>) call(inspect, "getMounts")) {
            Map<String,Object> m = new LinkedHashMap<>();
            for (String name : List.of("getName", "getSource", "getRW")) m.put(name.substring(3), call(mount, name));
            m.put("Destination", call(call(mount, "getDestination"), "getPath")); mounts.add(m);
        }
        result.put("mounts", mounts); return result;
    }
    static void migrate(Object fixture, String target, int expected) throws Throwable {
        stage = current.get("mode") + ":migrate_target_" + Objects.toString(target,"latest") + "_expected_" + expected;
        Object result = call(call(fixture, "flyway", target), "migrate");
        Object actual = result.getClass().getField("migrationsExecuted").get(result);
        requireEqual(expected, actual, "migration count"); passed("migrate target=" + Objects.toString(target,"latest") + " migrationsExecuted=" + actual);
    }
    static void validate(Object fixture, String target) throws Throwable {
        stage = current.get("mode") + ":validate_target_" + Objects.toString(target,"latest");
        call(call(fixture, "flyway", target), "validate"); passed("Flyway validate target=" + Objects.toString(target,"latest"));
    }
    static void captureDenial(Object fixture, List<Object> destination, String role, String sql, String expected) throws Throwable {
        stage = current.get("mode") + ":observed_denial_" + role;
        var before = snapshot(fixture, ALL); String actual = null; boolean rolledBack = false;
        try (Connection connection = (Connection) call(fixture, "connection", role); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try { statement.execute(sql); } catch (SQLException denied) { actual = denied.getSQLState(); }
            finally { connection.rollback(); rolledBack = true; }
        }
        requireEqual(expected, actual, "observed SQLSTATE");
        requireEqual(before, snapshot(fixture, ALL), "SQL denial authoritative rows unchanged");
        destination.add(Map.of("role",role,"authentication",EXECUTORS.contains(role) || role.equals("vra_owner") ? "actual migrator credential via accepted owner/executor SET path (NOLOGIN role)" : "actual role credential", "sql",sql,"sqlstate",actual,"transaction_rollback",rolledBack,"authoritative_rows_unchanged",true)); save();
    }
    static final String HISTORY = "SELECT installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success FROM vra.flyway_schema_history ORDER BY installed_rank";
    static final String HISTORY_PRIOR = "SELECT installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success FROM vra.flyway_schema_history WHERE version IN ('1','2','3') ORDER BY installed_rank";
    static final String SET_GRAPH = "WITH RECURSIVE paths(member,roleid) AS (SELECT member,roleid FROM pg_auth_members WHERE set_option UNION SELECT p.member,m.roleid FROM paths p JOIN pg_auth_members m ON m.member=p.roleid WHERE m.set_option) SELECT member.rolname,parent.rolname FROM paths p JOIN pg_roles member ON member.oid=p.member JOIN pg_roles parent ON parent.oid=p.roleid WHERE member.rolname LIKE 'vra_%' OR parent.rolname LIKE 'vra_%' ORDER BY member.rolname,parent.rolname";
    static final String PUBLIC_REL = "SELECT c.relname,c.relkind,a.privilege_type,a.is_grantable FROM pg_class c CROSS JOIN LATERAL aclexplode(coalesce(c.relacl,acldefault(CASE WHEN c.relkind='S' THEN 'S'::\"char\" ELSE 'r'::\"char\" END,c.relowner))) a WHERE c.relnamespace='vra'::regnamespace AND c.relkind IN ('r','S') AND a.grantee=0 ORDER BY c.relname,a.privilege_type";
    static final String PUBLIC_SCHEMA = "SELECT nspname,a.privilege_type,a.is_grantable FROM pg_namespace n CROSS JOIN LATERAL aclexplode(coalesce(n.nspacl,acldefault('n'::\"char\",n.nspowner))) a WHERE n.nspname='vra' AND a.grantee=0";
    static final String ACL = "SELECT c.relname,pg_get_userbyid(a.grantee),a.privilege_type,a.is_grantable FROM pg_class c CROSS JOIN LATERAL aclexplode(coalesce(c.relacl,acldefault('r'::\"char\",c.relowner))) a WHERE c.relnamespace='vra'::regnamespace AND c.relname IN ('account','external_identity_binding','organization','organization_membership','inventory_operation_grant','poc_account_record','poc_organization_record','browser_session','factor_challenge','factor_assertion_use','staff_role_assignment','security_role_proposal','security_role_approval','protected_security_audit','security_event','webhook_fixture_receipt','webhook_fixture_target') ORDER BY c.relname,pg_get_userbyid(a.grantee),a.privilege_type";
    static Map<?,?> snapshot(Object fixture, List<String> tables) throws Throwable { return (Map<?,?>)call(fixture,"snapshot",tables); }
    static Object rowCounts(Object fixture, List<String> tables) throws Throwable {
        List<Object> result = new ArrayList<>(); for(String t:tables) result.add(Map.of("table",t,"count",call(fixture,"value","SELECT count(*) FROM vra."+t))); return result;
    }
    static Object rows(Object fixture,String sql) throws Throwable { return call(fixture,"rows","postgres",sql); }
    static void passed(String name) throws Exception { ((List<Object>)current.get("stages")).add(Map.of("name",name,"status","PASS","at",Instant.now().toString())); save(); }
    static void requireEqual(Object expected,Object actual,String label) { if(!Objects.equals(expected,actual)) throw new AssertionError(label+" failed; values omitted from error"); }
    static Object field(Class<?> type,Object object,String name) throws Throwable { Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    static Object callStatic(String name,Object fixture) throws Throwable { Method m=TEST.getDeclaredMethod(name,FIXTURE);m.setAccessible(true);try{return m.invoke(null,fixture);}catch(InvocationTargetException e){throw unwrap(e);} }
    static Object call(Object object,String name,Object...args) throws Throwable {
        Method found=null;
        for(Class<?> c=object.getClass();c!=null && found==null;c=c.getSuperclass()) for(Method m:c.getDeclaredMethods()) if(m.getName().equals(name)&&m.getParameterCount()==args.length){boolean match=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!m.getParameterTypes()[i].isInstance(args[i]))match=false;if(match){found=m;break;}}
        if(found==null)throw new NoSuchMethodException(name);found.setAccessible(true);try{return found.invoke(object,args);}catch(InvocationTargetException e){throw unwrap(e);}
    }
    static Throwable unwrap(Throwable e){while(e instanceof InvocationTargetException && e.getCause()!=null)e=e.getCause();return e;}
    static void save() throws Exception { Files.writeString(destination,json(document)+"\n"); }
    static String json(Object o) {
        if(o==null)return "null"; if(o instanceof Boolean||o instanceof Number)return o.toString();
        if(o instanceof Map<?,?> m){List<String> v=new ArrayList<>();for(var e:m.entrySet())v.add(json(e.getKey().toString())+":"+json(e.getValue()));return "{"+String.join(",",v)+"}";}
        if(o instanceof Iterable<?> a){List<String>v=new ArrayList<>();for(Object x:a)v.add(json(x));return "["+String.join(",",v)+"]";}
        String s=o.toString();StringBuilder b=new StringBuilder("\"");for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}}return b.append('"').toString();
    }
}
