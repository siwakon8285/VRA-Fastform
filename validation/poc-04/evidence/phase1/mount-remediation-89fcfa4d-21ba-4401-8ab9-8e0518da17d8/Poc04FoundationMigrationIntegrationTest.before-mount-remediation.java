package dev.vra.migration;

import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URL;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Poc04FoundationMigrationIntegrationTest {

    private static final UUID RUN_ID = UUID.randomUUID();
    private static final AtomicInteger DATABASE_ORDINAL = new AtomicInteger();
    private static final String POSTGRES_IMAGE = "docker.io/library/postgres@sha256:"
            + "d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f";
    private static final String OWNER = "vra_owner";
    private static final String MIGRATOR = "vra_migrator";
    private static final String RUNTIME = "vra_runtime";
    private static final List<String> EXECUTORS = List.of(
            "vra_sync_executor", "vra_security_executor", "vra_telemetry_executor");
    private static final List<String> TEST_LOGINS = List.of(
            "vra_factor_fixture", "vra_audit_evidence_reader", "vra_security_telemetry_observer");
    private static final List<String> PRIOR_WORKLOADS = List.of(RUNTIME,
            "vra_outbox_worker", "vra_reconciliation_worker", "vra_async_operator",
            "vra_projection_rebuilder", "vra_async_observer");
    private static final List<String> FOUNDATION_TABLES = List.of(
            "account", "external_identity_binding", "organization", "organization_membership",
            "inventory_operation_grant", "poc_account_record", "poc_organization_record",
            "browser_session", "factor_challenge", "factor_assertion_use", "staff_role_assignment",
            "security_role_proposal", "security_role_approval", "protected_security_audit",
            "security_event", "webhook_fixture_receipt", "webhook_fixture_target");
    private static final List<String> PRIOR_TABLES = List.of(
            "inventory_balance", "inventory_reservation", "inventory_reservation_idempotency",
            "outbox_event", "outbox_delivery", "outbox_delivery_history", "consumer_inbox",
            "reservation_projection", "reconciliation_case", "reconciliation_history");

    // Reviewed Phase-0 source binding. These are source hashes, not Flyway CRC checksums.
    private static final Map<String, String> PRIOR_SOURCE_HASHES = Map.of(
            "V1__inventory_reservation_foundation.sql",
            "d804cbe5153b9a904ae0747af55c8295079d4a0c3edb4a38145ab5199b5b59c4",
            "V2__inventory_reservation_idempotency.sql",
            "7bef750b59b7b77e8402f4f42c13c21c4e072092504b9f268bace9b72de8d2bb",
            "V3__outbox_recovery.sql",
            "9edbd15ffe08ee4edafa3f549856ee38a782485c54db9c73e69d4263e5ca26b4");

    @Test
    void phaseOneContainsOnlyV1ThroughV4AndPreservesReviewedPriorSourceBytes() throws Exception {
        URL resources = getClass().getClassLoader().getResource("db/migration");
        assertNotNull(resources, "migration resources must be available");
        Path migrationDirectory = Path.of(resources.toURI());
        Set<String> actual;
        try (var files = Files.list(migrationDirectory)) {
            actual = files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
        assertEquals(Set.of(
                "V1__inventory_reservation_foundation.sql",
                "V2__inventory_reservation_idempotency.sql",
                "V3__outbox_recovery.sql",
                "V4__poc04_identity_security_foundation.sql"), actual,
                "Phase 1 requires V4 and excludes later migrations");
        for (var prior : PRIOR_SOURCE_HASHES.entrySet()) {
            assertEquals(prior.getValue(), HexFormat.of().formatHex(MessageDigest
                    .getInstance("SHA-256").digest(Files.readAllBytes(
                            migrationDirectory.resolve(prior.getKey())))), prior.getKey());
        }
    }

    @Test
    void collectorPreparationRestrictsSourceAndDefinesNoObserverProcess() throws Exception {
        Path directory = repositoryRoot().resolve("validation/poc-04/db");
        String profile = Files.readString(directory.resolve("postgresql-collector-test.conf"));
        for (String setting : List.of("logging_collector = on", "log_destination = 'jsonlog'", "lc_messages = 'C'",
                "log_error_verbosity = 'verbose'", "log_min_error_statement = 'error'", "log_statement = 'none'",
                "log_parameter_max_length = 0", "log_parameter_max_length_on_error = 0", "log_file_mode = 0640",
                "log_directory = '/var/log/vra-poc04-collector'")) {
            assertTrue(profile.lines().anyMatch(setting::equals), "required restricted TEST collector setting: " + setting);
        }
        String entrypoint = Files.readString(directory.resolve("collector-test-entrypoint.sh"));
        assertTrue(entrypoint.contains("install -d -o postgres -g 9404 -m 2750"), "admin-owned source with restricted group/read mode");
        String compose = Files.readString(directory.resolve("collector-test.compose.yaml"));
        assertTrue(compose.contains("user: \"9404:9404\""));
        assertTrue(compose.contains("read_only: true"));
        assertTrue(compose.contains("profiles: [poc04-collector-test]"));
        String executableServices = compose.substring(compose.indexOf("\nservices:\n"), compose.indexOf("\nsecrets:\n"));
        assertEquals(1, executableServices.lines().filter(line -> line.matches("  [a-zA-Z0-9_-]+:")).count(),
                "only PostgreSQL is an executable service; observer mount is reserved metadata");
        assertTrue(!compose.contains("docker.sock") && !compose.contains("observer.py")
                && !compose.contains("pg_read_server_files") && !compose.contains("pg_monitor"));
    }

    @Test
    void sourceBindingIncludesTheWholeReviewedRepositoryAndUntrackedPhaseOneBackendSource() throws Exception {
        SourceBinding first = sourceBinding();
        assertTrue(first.files() > 100, "binding must contain the repository and authority documents, beyond selected Phase-1 files");
        assertEquals(first, sourceBinding(), "canonical full-source binding is deterministic without a clock or run ID");
    }

    @Test
    void logicalDatabaseIdentityUsesStableUuidV5UnderOneUuidV4RunNamespace() throws Exception {
        assertEquals(4, RUN_ID.version());
        assertEquals(UUID.fromString("2ed6657d-e927-568b-95e1-2665a8aea6a2"), uuidV5(
                UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"), "www.example.com"));
        UUID first = uuidV5(RUN_ID, "postgres-authority/0");
        assertEquals(5, first.version());
        assertEquals(first, uuidV5(RUN_ID, "postgres-authority/0"));
        assertTrue(!first.equals(uuidV5(RUN_ID, "postgres-authority/1")), "a newly initialized logical ordinal is a distinct instance");
    }

    @Test
    @Tag("postgres")
    void freshV1ThroughV4ValidatesRerunsAndEnforcesPhaseOneBoundary() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertEquals(4, fixture.flyway(null).migrate().migrationsExecuted);
            fixture.flyway(null).validate();
            proveCheckpoint(fixture);
        }
    }

    @Test
    @Tag("postgres")
    void populatedV3UpgradeRetainsEveryPriorTableAndFlywayHistory() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertEquals(3, fixture.flyway("3").migrate().migrationsExecuted);
            fixture.flyway("3").validate();
            seedPriorState(fixture);
            Map<String, String> priorData = fixture.snapshot(PRIOR_TABLES);
            List<List<String>> priorHistory = fixture.rows("postgres", """
                    SELECT installed_rank, version, description, type, script, checksum,
                           installed_by, installed_on, execution_time, success
                    FROM vra.flyway_schema_history ORDER BY installed_rank
                    """);
            List<List<String>> priorCatalog = fixture.priorCatalog();
            assertEquals(1, fixture.flyway(null).migrate().migrationsExecuted);
            fixture.flyway(null).validate();
            assertEquals(priorData, fixture.snapshot(PRIOR_TABLES), "all accepted V1-V3 rows retained");
            assertEquals(priorCatalog, fixture.priorCatalog(), "V1-V3 owner, table and column ACL retained");
            assertEquals(priorHistory, fixture.rows("postgres", """
                    SELECT installed_rank, version, description, type, script, checksum,
                           installed_by, installed_on, execution_time, success
                    FROM vra.flyway_schema_history WHERE version IN ('1','2','3')
                    ORDER BY installed_rank
                    """), "V1-V3 Flyway checksums and complete history retained");
            proveCheckpoint(fixture);
            assertEquals(priorData, fixture.snapshot(PRIOR_TABLES), "negative V4 proof retains prior state");
        }
    }

    @Test
    @Tag("postgres")
    void administratorBootstrapConvergesAndRejectsUnexpectedMembershipDrift() throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<List<String>> before = fixture.roleState();
            fixture.securityBootstrap();
            assertEquals(before, fixture.roleState(), "bootstrap rerun has exact same attributes and memberships");
            fixture.sql("postgres", "ALTER ROLE vra_sync_executor LOGIN INHERIT");
            fixture.securityBootstrap();
            assertEquals(before, fixture.roleState(), "restrictive role attributes converge after reported drift");
            SQLException ordinaryFailure = assertThrows(SQLException.class, () -> fixture.sql(RUNTIME,
                    Files.readString(repositoryRoot().resolve("validation/poc-04/db/bootstrap-security-roles.sql"))));
            assertEquals("P0001", ordinaryFailure.getSQLState(), "ordinary workload cannot bootstrap administrator roles");
            assertEquals(before, fixture.roleState(), "ordinary bootstrap denial changes no role state");
            fixture.sql("postgres", "GRANT CREATE ON DATABASE vra_poc01 TO vra_factor_fixture");
            SQLException grantFailure = assertThrows(SQLException.class, fixture::securityBootstrap);
            assertEquals("P0001", grantFailure.getSQLState(), "unexpected database privilege drift fails closed");
            assertEquals("t", fixture.value("SELECT has_database_privilege('vra_factor_fixture','vra_poc01','CREATE')"),
                    "unexpected privilege is reported without hidden revocation");
            fixture.sql("postgres", "REVOKE CREATE ON DATABASE vra_poc01 FROM vra_factor_fixture");
            fixture.sql("postgres", "CREATE ROLE vra_unplanned_helper LOGIN NOINHERIT");
            fixture.sql("postgres", "GRANT vra_security_executor TO vra_unplanned_helper WITH SET TRUE");
            SQLException failure = assertThrows(SQLException.class, fixture::securityBootstrap);
            assertEquals("P0001", failure.getSQLState(), "unexpected workload SET membership fails closed");
            assertEquals("t", fixture.value("SELECT pg_has_role('vra_unplanned_helper','vra_security_executor','SET')"),
                    "bootstrap must report preexisting drift rather than conceal it");
            assertEquals(before, fixture.roleStateExcluding("vra_unplanned_helper"),
                    "failed bootstrap leaves accepted role state unchanged");
        }
    }

    private static void proveCheckpoint(Fixture fixture) throws Exception {
        assertEquals("170011", fixture.value("SHOW server_version_num"), "executed PostgreSQL version pin");
        assertEquals(List.of("1", "2", "3", "4"), fixture.column("""
                SELECT version FROM vra.flyway_schema_history WHERE success ORDER BY installed_rank
                """));
        assertEquals("1", fixture.value("SELECT count(*) FROM vra.flyway_schema_history WHERE version='4'"));
        assertEquals("22", fixture.value("SELECT count(*) FROM pg_proc WHERE pronamespace='vra'::regnamespace"),
                "only existing V3 functions exist; Phase 2 has not started");
        Map<String, String> allData = fixture.snapshot(allTables());
        List<List<String>> history = fixture.rows("postgres", "SELECT * FROM vra.flyway_schema_history ORDER BY installed_rank");
        List<List<String>> catalog = fixture.catalog();
        assertEquals(0, fixture.flyway(null).migrate().migrationsExecuted);
        fixture.flyway(null).validate();
        assertEquals(allData, fixture.snapshot(allTables()), "same DB migration rerun preserves every data row");
        assertEquals(history, fixture.rows("postgres", "SELECT * FROM vra.flyway_schema_history ORDER BY installed_rank"));
        assertEquals(catalog, fixture.catalog(), "same DB rerun preserves catalog and ACL state");
        verifyRoleBoundary(fixture);
        verifyFoundationCatalog(fixture);
        verifyGrantsAndSqlDenials(fixture);
        verifyDataModelNegatives(fixture);
    }

    private static List<String> allTables() {
        List<String> tables = new ArrayList<>(PRIOR_TABLES);
        tables.addAll(FOUNDATION_TABLES);
        return tables;
    }

    private static void verifyRoleBoundary(Fixture fixture) throws Exception {
        for (String role : concat(EXECUTORS, TEST_LOGINS)) {
            assertEquals(List.of(List.of(TEST_LOGINS.contains(role) ? "t" : "f", "f", "f", "f", "f", "f", "f")),
                    fixture.rows("postgres", "SELECT rolcanlogin,rolinherit,rolsuper,rolcreatedb,rolcreaterole,"
                            + "rolreplication,rolbypassrls FROM pg_roles WHERE rolname='" + role + "'"), role);
        }
        Set<String> direct = new TreeSet<>(fixture.column("""
                SELECT member.rolname||'->'||parent.rolname||':'||m.set_option||':'||m.inherit_option||':'||m.admin_option
                FROM pg_auth_members m JOIN pg_roles member ON member.oid=m.member
                JOIN pg_roles parent ON parent.oid=m.roleid
                WHERE member.rolname LIKE 'vra_%' OR parent.rolname LIKE 'vra_%'
                """));
        Set<String> expectedDirect = new TreeSet<>(Set.of(
                "vra_migrator->vra_owner:true:false:false",
                "vra_owner->vra_async_executor:true:false:false"));
        for (String role : EXECUTORS) expectedDirect.add("vra_owner->" + role + ":true:false:false");
        assertEquals(expectedDirect, direct, "complete direct VRA role graph");
        Set<String> expectedSet = new TreeSet<>(Set.of(
                "vra_migrator->vra_owner", "vra_migrator->vra_async_executor", "vra_owner->vra_async_executor"));
        for (String role : EXECUTORS) {
            expectedSet.add("vra_owner->" + role);
            expectedSet.add("vra_migrator->" + role);
        }
        assertEquals(expectedSet, new TreeSet<>(fixture.column("""
                WITH RECURSIVE paths(member,roleid) AS (
                    SELECT member,roleid FROM pg_auth_members WHERE set_option
                    UNION
                    SELECT p.member,m.roleid FROM paths p JOIN pg_auth_members m ON m.member=p.roleid
                    WHERE m.set_option
                ) SELECT member.rolname||'->'||parent.rolname
                  FROM paths p JOIN pg_roles member ON member.oid=p.member
                  JOIN pg_roles parent ON parent.oid=p.roleid
                  WHERE member.rolname LIKE 'vra_%' OR parent.rolname LIKE 'vra_%'
                """)), "complete transitive SET graph includes accepted migrator administrative path");
        for (String role : concat(PRIOR_WORKLOADS, TEST_LOGINS)) {
            for (String executor : EXECUTORS) {
                assertEquals("f", fixture.value("SELECT pg_has_role('" + role + "','" + executor + "','SET')"));
                fixture.denied(role, "SET ROLE " + executor, "42501");
            }
            fixture.denied(role, "CREATE TABLE vra.forbidden_ddl(id bigint)", "42501");
            fixture.denied(role, "ALTER ROLE vra_security_executor LOGIN", "42501");
            fixture.denied(role, "GRANT vra_security_executor TO " + role, "42501");
        }
        for (String executor : EXECUTORS) {
            fixture.sql(MIGRATOR, "SET ROLE " + executor);
            fixture.denied(executor, "CREATE TABLE vra.forbidden_executor_ddl(id bigint)", "42501");
            assertEquals("t", fixture.value("SELECT has_schema_privilege('" + executor + "','vra','USAGE')"));
        }
        assertEquals(Set.of("postgres", OWNER), new TreeSet<>(fixture.column("""
                SELECT rolname FROM pg_roles WHERE has_schema_privilege(oid,'vra','CREATE')
                """)), "only administrator and authoritative owner retain schema CREATE");
        assertEquals("0", fixture.value("""
                SELECT count(*) FROM pg_class c CROSS JOIN LATERAL aclexplode(
                    coalesce(c.relacl,acldefault(CASE WHEN c.relkind='S' THEN 'S'::"char" ELSE 'r'::"char" END,c.relowner))) a
                WHERE c.relnamespace='vra'::regnamespace AND c.relkind IN ('r','S') AND a.grantee=0
                """), "PUBLIC has no table or sequence grants");
        assertEquals("0", fixture.value("""
                SELECT count(*) FROM pg_namespace n CROSS JOIN LATERAL aclexplode(
                    coalesce(n.nspacl,acldefault('n'::"char",n.nspowner))) a
                WHERE n.nspname='vra' AND a.grantee=0
                """), "PUBLIC has no trusted-schema grants");
        for (String role : TEST_LOGINS) {
            assertEquals("f", fixture.value("SELECT pg_has_role('" + role + "','pg_read_server_files','MEMBER')"
                    + " OR pg_has_role('" + role + "','pg_monitor','MEMBER')"));
            assertEquals("f", fixture.value("SELECT has_parameter_privilege('" + role + "','log_statement','SET')"));
        }
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private static void verifyFoundationCatalog(Fixture fixture) throws SQLException {
        Map<String, String> primaryKeys = Map.ofEntries(
                Map.entry("account", "account_id"),
                Map.entry("external_identity_binding", "issuer,subject"),
                Map.entry("organization", "org_id"),
                Map.entry("organization_membership", "account_id,org_id"),
                Map.entry("inventory_operation_grant", "account_id,owner_id,operation"),
                Map.entry("poc_account_record", "record_id"),
                Map.entry("poc_organization_record", "record_id"),
                Map.entry("browser_session", "session_id"),
                Map.entry("factor_challenge", "challenge_id"),
                Map.entry("factor_assertion_use", "jti"),
                Map.entry("staff_role_assignment", "account_id,role_code,scope_id"),
                Map.entry("security_role_proposal", "proposal_id"),
                Map.entry("security_role_approval", "approval_id"),
                Map.entry("protected_security_audit", "audit_id"),
                Map.entry("security_event", "event_id"),
                Map.entry("webhook_fixture_receipt", "event_id"),
                Map.entry("webhook_fixture_target", "target_id"));
        for (String table : FOUNDATION_TABLES) {
            assertEquals(OWNER, fixture.value("SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='vra." + table + "'::regclass"), table);
            assertEquals(primaryKeys.get(table), fixture.value("""
                    SELECT string_agg(a.attname,',' ORDER BY keys.ordinality)
                    FROM pg_constraint c CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY keys(attnum,ordinality)
                    JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=keys.attnum
                    WHERE c.conrelid='vra.%s'::regclass AND c.contype='p'
                    """.formatted(table)), "authority primary key: " + table);
            assertEquals("0", fixture.value("SELECT count(*) FROM pg_constraint WHERE conrelid='vra." + table + "'::regclass AND NOT convalidated"),
                    "all foundation constraints validated: " + table);
            assertEquals("0", fixture.value("SELECT count(*) FROM pg_index WHERE indrelid='vra." + table + "'::regclass AND (NOT indisvalid OR NOT indisready)"),
                    "all foundation indexes usable: " + table);
        }
        for (var key : Map.of(
                "external_identity_binding", "account_id",
                "organization_membership", "account_id,org_id",
                "poc_account_record", "owner_account_id",
                "poc_organization_record", "owner_org_id",
                "browser_session", "account_id",
                "staff_role_assignment", "account_id",
                "webhook_fixture_receipt", "target_id").entrySet()) {
            for (String column : key.getValue().split(",")) {
                assertTrue(Integer.parseInt(fixture.value("""
                        SELECT count(*) FROM pg_constraint c JOIN pg_attribute a
                            ON a.attrelid=c.conrelid AND a.attnum=ANY(c.conkey)
                        WHERE c.conrelid='vra.%s'::regclass AND c.contype='f' AND a.attname='%s'
                        """.formatted(key.getKey(), column))) > 0, "required FK: " + key.getKey() + "." + column);
            }
        }
        assertEquals("0", fixture.value("""
                SELECT count(*) FROM pg_constraint c JOIN pg_attribute a
                    ON a.attrelid=c.conrelid AND c.conkey=ARRAY[a.attnum]::smallint[]
                WHERE c.conrelid='vra.external_identity_binding'::regclass AND c.contype IN ('u','p') AND a.attname='account_id'
                """), "multiple reviewed identities may bind the same account");
        assertEquals("1", fixture.value("""
                SELECT count(*) FROM pg_constraint c JOIN pg_attribute a
                    ON a.attrelid=c.conrelid AND c.conkey=ARRAY[a.attnum]::smallint[]
                WHERE c.conrelid='vra.browser_session'::regclass AND c.contype='u' AND a.attname='verifier'
                """), "session verifier unique");
        assertEquals("1", fixture.value("""
                SELECT count(*) FROM pg_constraint c JOIN pg_attribute a
                    ON a.attrelid=c.conrelid AND c.conkey=ARRAY[a.attnum]::smallint[]
                WHERE c.conrelid='vra.security_role_approval'::regclass AND c.contype='u' AND a.attname='proposal_id'
                """), "approval unique per proposal");
        assertEquals("1", fixture.value("SELECT count(*) FROM pg_type WHERE typnamespace='vra'::regnamespace AND typname='audit_evidence_row' AND typtype='c'"));
        Set<String> tableNames = new TreeSet<>(FOUNDATION_TABLES);
        String foundationNames = tableNames.stream().map(name -> "'" + name + "'").collect(Collectors.joining(","));
        Set<String> expectedForeignKeys = Set.of(
                "external_identity_binding(account_id)->account(account_id)",
                "organization_membership(account_id)->account(account_id)",
                "organization_membership(org_id)->organization(org_id)",
                "inventory_operation_grant(account_id)->account(account_id)",
                "poc_account_record(owner_account_id)->account(account_id)",
                "poc_organization_record(owner_org_id)->organization(org_id)",
                "browser_session(account_id)->account(account_id)",
                "factor_challenge(actor_account_id)->account(account_id)",
                "factor_challenge(session_id,actor_account_id,session_generation)->browser_session(session_id,account_id,generation)",
                "factor_assertion_use(challenge_id,actor_account_id,session_id,session_generation,operation,context_digest)->factor_challenge(challenge_id,actor_account_id,session_id,session_generation,operation,context_digest)",
                "staff_role_assignment(account_id)->account(account_id)",
                "security_role_proposal(maker_account_id)->account(account_id)",
                "security_role_proposal(maker_session_id,maker_account_id,maker_session_generation)->browser_session(session_id,account_id,generation)",
                "security_role_proposal(target_account_id)->account(account_id)",
                "security_role_approval(proposal_id,proposal_digest,proposal_version)->security_role_proposal(proposal_id,change_digest,version)",
                "security_role_approval(checker_account_id)->account(account_id)",
                "security_role_approval(checker_session_id,checker_account_id,checker_session_generation)->browser_session(session_id,account_id,generation)",
                "security_role_approval(challenge_id,checker_account_id,context_digest)->factor_challenge(challenge_id,actor_account_id,context_digest)",
                "protected_security_audit(actor_account_id)->account(account_id)",
                "protected_security_audit(subject_account_id)->account(account_id)",
                "protected_security_audit(proposal_id)->security_role_proposal(proposal_id)",
                "protected_security_audit(approval_id)->security_role_approval(approval_id)",
                "protected_security_audit(approval_id,proposal_id)->security_role_approval(approval_id,proposal_id)",
                "security_event(actor_account_id)->account(account_id)",
                "webhook_fixture_receipt(target_id)->webhook_fixture_target(target_id)");
        assertEquals(expectedForeignKeys, new TreeSet<>(fixture.column("""
                SELECT child.relname||'('||(
                    SELECT string_agg(a.attname,',' ORDER BY k.ordinality)
                    FROM unnest(c.conkey) WITH ORDINALITY k(attnum,ordinality)
                    JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum
                )||')->'||parent.relname||'('||(
                    SELECT string_agg(a.attname,',' ORDER BY k.ordinality)
                    FROM unnest(c.confkey) WITH ORDINALITY k(attnum,ordinality)
                    JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.attnum
                )||')'
                FROM pg_constraint c JOIN pg_class child ON child.oid=c.conrelid JOIN pg_class parent ON parent.oid=c.confrelid
                WHERE c.contype='f' AND c.connamespace='vra'::regnamespace AND child.relname IN (%s)
                """.formatted(foundationNames))), "every stated FK including exact factor/proposal/approval/audit authority bindings");
        Set<String> expectedUnique = Set.of(
                "browser_session(verifier)", "browser_session(session_id,account_id,generation)",
                "factor_challenge(challenge_id,actor_account_id,session_id,session_generation,operation,context_digest)",
                "factor_challenge(challenge_id,actor_account_id,context_digest)",
                "security_role_proposal(proposal_id,change_digest,version)",
                "security_role_approval(proposal_id)", "security_role_approval(approval_id,proposal_id)",
                "security_event(source_run_id,source_event_digest)");
        assertEquals(expectedUnique, new TreeSet<>(fixture.column("""
                SELECT child.relname||'('||(
                    SELECT string_agg(a.attname,',' ORDER BY k.ordinality)
                    FROM unnest(c.conkey) WITH ORDINALITY k(attnum,ordinality)
                    JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum
                )||')' FROM pg_constraint c JOIN pg_class child ON child.oid=c.conrelid
                WHERE c.contype='u' AND c.connamespace='vra'::regnamespace AND child.relname IN (%s)
                """.formatted(foundationNames))), "all foundation uniqueness authority tuples");
        Set<String> requiredLookupIndexes = Set.of(
                "external_identity_binding_account_idx", "organization_membership_scope_idx", "inventory_operation_grant_owner_idx",
                "poc_account_record_owner_idx", "poc_organization_record_owner_idx", "browser_session_account_idx",
                "factor_challenge_session_idx", "factor_assertion_use_challenge_idx", "staff_role_assignment_scope_idx",
                "security_role_proposal_maker_idx", "security_role_proposal_target_idx", "security_role_approval_checker_idx",
                "protected_security_audit_time_idx", "protected_security_audit_actor_idx", "protected_security_audit_subject_idx",
                "protected_security_audit_proposal_idx", "security_event_time_idx", "security_event_actor_idx", "webhook_fixture_receipt_target_idx");
        assertEquals(requiredLookupIndexes, new TreeSet<>(fixture.column("""
                SELECT i.relname FROM pg_index x JOIN pg_class i ON i.oid=x.indexrelid JOIN pg_class t ON t.oid=x.indrelid
                WHERE t.relnamespace='vra'::regnamespace AND t.relname IN (%s)
                    AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid=i.oid)
                """.formatted(foundationNames))), "required owner/scope/time lookup indexes all exist");
        assertEquals("0", fixture.value("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema='vra' AND table_name IN (%s)
                    AND column_name LIKE '%%\u005fid' ESCAPE '\\'
                    AND NOT (table_name='webhook_fixture_receipt' AND column_name='event_id') AND data_type<>'uuid'
                """.formatted(foundationNames)), "all internal IDs use UUID");
        assertEquals("character varying:255", fixture.value("""
                SELECT data_type||':'||character_maximum_length FROM information_schema.columns
                WHERE table_schema='vra' AND table_name='webhook_fixture_receipt' AND column_name='event_id'
                """));
        assertEquals("0", fixture.value("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema='vra' AND table_name IN (%s)
                    AND (column_name LIKE '%%version' OR column_name LIKE '%%generation') AND data_type<>'bigint'
                """.formatted(foundationNames)), "all version/generation fields use bigint");
        assertEquals("0", fixture.value("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema='vra' AND table_name IN (%s)
                    AND (column_name LIKE '%%\u005fat' ESCAPE '\\' OR column_name='source_time')
                    AND data_type<>'timestamp with time zone'
                """.formatted(foundationNames)), "all time fields use timestamptz");
        assertEquals("0", fixture.value("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema='vra' AND table_name IN (%s)
                    AND (column_name ~* 'bearer|private_key|raw_body|signature|secret|email|display_name')
                """.formatted(foundationNames)), "authority schema contains no raw secrets or profile-based authority");
    }

    private static void verifyGrantsAndSqlDenials(Fixture fixture) throws SQLException {
        Set<String> runtimeSelect = new TreeSet<>(FOUNDATION_TABLES);
        runtimeSelect.remove("protected_security_audit");
        runtimeSelect.remove("security_event");
        Map<String, Set<String>> columnUpdates = Map.of(
                "poc_account_record", Set.of("label", "version"),
                "poc_organization_record", Set.of("label", "version"),
                "webhook_fixture_receipt", Set.of("status"),
                "webhook_fixture_target", Set.of("state", "version"));
        for (String table : FOUNDATION_TABLES) {
            assertEquals(runtimeSelect.contains(table) ? "t" : "f",
                    fixture.value("SELECT has_table_privilege('vra_runtime','vra." + table + "','SELECT')"),
                    "runtime direct SELECT: " + table);
            for (String privilege : List.of("INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                assertEquals(table.equals("webhook_fixture_receipt") && privilege.equals("INSERT") ? "t" : "f",
                        fixture.value("SELECT has_table_privilege('vra_runtime','vra." + table + "','" + privilege + "')"),
                        "runtime table-wide " + privilege + ": " + table);
            }
            for (String column : fixture.column("SELECT attname FROM pg_attribute WHERE attrelid='vra." + table
                    + "'::regclass AND attnum>0 AND NOT attisdropped ORDER BY attnum")) {
                assertEquals(columnUpdates.getOrDefault(table, Set.of()).contains(column) ? "t" : "f",
                        fixture.value("SELECT has_column_privilege('vra_runtime','vra." + table + "','" + column + "','UPDATE')"),
                        "runtime exact UPDATE column: " + table + "." + column);
            }
            fixture.denied(RUNTIME, "DELETE FROM vra." + table, "42501");
            fixture.denied(RUNTIME, "TRUNCATE vra." + table, "42501");
            if (!Set.of("webhook_fixture_receipt", "security_event").contains(table)) {
                fixture.denied(RUNTIME, "INSERT INTO vra." + table + " DEFAULT VALUES", "42501");
            }
            for (String role : concat(EXECUTORS, concat(PRIOR_WORKLOADS.subList(1, PRIOR_WORKLOADS.size()), TEST_LOGINS))) {
                for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                    assertEquals("f", fixture.value("SELECT has_table_privilege('" + role + "','vra." + table + "','" + privilege + "')"),
                            "no early capability or workload grant: " + role + " " + table + " " + privilege);
                    if (List.of("SELECT", "INSERT", "UPDATE", "REFERENCES").contains(privilege)) {
                        assertEquals("f", fixture.value("SELECT has_any_column_privilege('" + role + "','vra." + table + "','" + privilege + "')"),
                                "no new column privilege: " + role + " " + table + " " + privilege);
                    }
                }
            }
        }
        for (String table : List.of("poc_account_record", "poc_organization_record")) {
            String owner = table.equals("poc_account_record") ? "owner_account_id" : "owner_org_id";
            fixture.denied(RUNTIME, "UPDATE vra." + table + " SET " + owner + "=gen_random_uuid()", "42501");
            fixture.denied(RUNTIME, "UPDATE vra." + table + " SET state='CLOSED'", "42501");
        }
        for (String table : List.of("browser_session", "factor_challenge", "factor_assertion_use",
                "staff_role_assignment", "security_role_proposal", "security_role_approval")) {
            fixture.denied(RUNTIME, "UPDATE vra." + table + " SET "
                    + (Set.of("security_role_approval", "factor_challenge").contains(table) ? "consumed_at=now()"
                        : table.equals("browser_session") ? "generation=generation+1" : "state=state"), "42501");
        }
        fixture.denied(RUNTIME, "SELECT * FROM vra.protected_security_audit", "42501");
        fixture.denied(RUNTIME, "INSERT INTO vra.security_event(source_kind) VALUES ('POSTGRES_ACL')", "42501");
        for (String column : List.of("source_kind", "observer_workload", "source_run_id", "source_event_digest", "source_time", "db_operation")) {
            assertEquals("f", fixture.value("SELECT has_column_privilege('vra_runtime','vra.security_event','" + column + "','INSERT')"),
                    "runtime cannot write observer provenance: " + column);
        }
        Set<String> appColumns = Set.of("event_id", "actor_type", "actor_account_id", "actor_workload", "event_code",
                "outcome_code", "occurred_at", "request_id", "target_type", "target_id", "target_reference", "risk_code");
        Set<String> safeSelectColumns = new TreeSet<>(appColumns);
        safeSelectColumns.add("source_kind");
        for (String privilege : List.of("INSERT", "SELECT")) {
            assertEquals(privilege.equals("INSERT") ? appColumns : safeSelectColumns, new TreeSet<>(fixture.column("""
                    SELECT attname FROM pg_attribute WHERE attrelid='vra.security_event'::regclass AND attnum>0 AND NOT attisdropped
                        AND has_column_privilege('vra_runtime',attrelid,attnum,'%s')
                    """.formatted(privilege))), "exact APP-origin runtime " + privilege + " columns");
        }
        fixture.rows(RUNTIME, "SELECT " + String.join(",", new TreeSet<>(safeSelectColumns)) + " FROM vra.security_event");
        fixture.denied(RUNTIME, "SELECT source_event_digest FROM vra.security_event", "42501");
        fixture.denied(RUNTIME, "UPDATE vra.security_role_proposal SET target_account_id=gen_random_uuid(),scope_id=gen_random_uuid(),change_digest=decode(repeat('00',32),'hex')", "42501");
        for (String privilege : List.of("SELECT", "INSERT", "UPDATE")) {
            assertEquals("t", fixture.value("SELECT has_table_privilege('vra_runtime','vra.inventory_reservation_idempotency','" + privilege + "')"),
                    "disclosed V2 rights remain during Phase 1: " + privilege);
        }
        Map<String, String> priorData = fixture.snapshot(PRIOR_TABLES);
        try (Connection connection = fixture.connection(RUNTIME); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            assertEquals(1, statement.executeUpdate("""
                    INSERT INTO vra.inventory_reservation_idempotency VALUES
                    ('phase-one-residual','terminal-proof',1,repeat('d',64),'REJECTED',NULL,NULL,'INSUFFICIENT_STOCK',now(),now())
                    """));
            assertEquals(1, statement.executeUpdate("""
                    UPDATE vra.inventory_reservation_idempotency SET request_fingerprint=repeat('e',64)
                    WHERE actor_scope='phase-one-residual' AND idempotency_key='terminal-proof'
                    """));
            try (ResultSet result = statement.executeQuery("""
                    SELECT request_fingerprint FROM vra.inventory_reservation_idempotency
                    WHERE actor_scope='phase-one-residual' AND idempotency_key='terminal-proof'
                    """)) {
                assertTrue(result.next());
                assertEquals("e".repeat(64), result.getString(1),
                        "Phase 1 explicitly retains disclosed V2 terminal rewrite residual");
            }
            connection.rollback();
        }
        assertEquals(priorData, fixture.snapshot(PRIOR_TABLES), "V2 residual capability proof rolls back test-only rows");
        assertThrows(FlywayException.class, () -> new MigrationRunner().migrate(
                fixture.database.getJdbcUrl(), RUNTIME, fixture.passwords.get(RUNTIME)), "runtime cannot migrate through owner role");
    }

    private static void verifyDataModelNegatives(Fixture fixture) throws SQLException {
        String maker = fixture.rows(OWNER, "INSERT INTO vra.account DEFAULT VALUES RETURNING account_id").getFirst().getFirst();
        String checker = fixture.rows(OWNER, "INSERT INTO vra.account DEFAULT VALUES RETURNING account_id").getFirst().getFirst();
        String target = fixture.rows(OWNER, "INSERT INTO vra.account DEFAULT VALUES RETURNING account_id").getFirst().getFirst();
        String organization = fixture.rows(OWNER, "INSERT INTO vra.organization DEFAULT VALUES RETURNING org_id").getFirst().getFirst();
        String scope = UUID.randomUUID().toString();
        fixture.sql(OWNER, "INSERT INTO vra.external_identity_binding(issuer,subject,account_id) VALUES ('https://issuer.fixture.invalid','subject-a','" + maker + "'),('https://issuer.fixture.invalid','subject-b','" + maker + "')");
        fixture.sql(OWNER, "INSERT INTO vra.organization_membership(account_id,org_id) VALUES ('" + maker + "','" + organization + "')");
        String inventoryOwner = UUID.randomUUID().toString();
        fixture.sql(OWNER, "INSERT INTO vra.inventory_operation_grant(account_id,owner_id,operation) VALUES ('" + maker + "','" + inventoryOwner + "','RESERVE')");
        fixture.sql(OWNER, "INSERT INTO vra.poc_account_record(owner_account_id,label) VALUES ('" + maker + "','fixture')");
        fixture.sql(OWNER, "INSERT INTO vra.poc_organization_record(owner_org_id,label) VALUES ('" + organization + "','fixture')");
        String makerSession = seedSession(fixture, maker, "01");
        String checkerSession = seedSession(fixture, checker, "02");
        String makerChallenge = seedChallenge(fixture, maker, makerSession, "MAKER_AUTH", "03");
        String checkerChallenge = seedChallenge(fixture, checker, checkerSession, "STAFF_SECURITY_GRANT", "04");
        fixture.sql(OWNER, """
                INSERT INTO vra.factor_assertion_use(jti,challenge_id,actor_account_id,session_id,session_generation,
                    operation,context_digest,jws_digest,attested_at,expires_at)
                VALUES ('fixture-assertion','%s','%s','%s',0,'MAKER_AUTH',decode(repeat('03',32),'hex'),
                    decode(repeat('05',32),'hex'),now(),now()+interval '60 seconds')
                """.formatted(makerChallenge, maker, makerSession));
        fixture.sql(OWNER, "INSERT INTO vra.staff_role_assignment(account_id,role_code,scope_id,granted_at) VALUES ('"
                + maker + "','STAFF_SECURITY_GRANT_MAKER','" + scope + "',now())");
        String proposal = fixture.rows(OWNER, """
                INSERT INTO vra.security_role_proposal(maker_account_id,maker_session_id,maker_session_generation,
                    target_account_id,role_code,scope_id,reason,change_digest,created_at,expires_at)
                VALUES ('%s','%s',0,'%s','SECURITY_AUDIT_REVIEWER','%s','fixture reason',
                    decode(repeat('04',32),'hex'),now(),now()+interval '15 minutes') RETURNING proposal_id
                """.formatted(maker, makerSession, target, scope)).getFirst().getFirst();
        fixture.sql(OWNER, """
                INSERT INTO vra.security_role_approval(proposal_id,proposal_digest,proposal_version,checker_account_id,
                    checker_session_id,checker_session_generation,challenge_id,context_digest,approved_at,expires_at)
                VALUES ('%s',decode(repeat('04',32),'hex'),1,'%s','%s',0,'%s',decode(repeat('04',32),'hex'),
                    now(),now()+interval '5 minutes')
                """.formatted(proposal, checker, checkerSession, checkerChallenge));
        fixture.sql(OWNER, """
                INSERT INTO vra.protected_security_audit(actor_type,actor_account_id,subject_account_id,action_code,
                    result_code,target_type,target_id,reason,request_id,proposal_id,delta_code,
                    subject_auth_generation_before,subject_auth_generation_after)
                VALUES ('HUMAN','%s','%s','STAFF_GRANT_EXECUTED','SUCCEEDED','STAFF_ROLE','%s',
                    'fixture reason',gen_random_uuid(),'%s','ROLE_GRANTED',0,1)
                """.formatted(maker, target, target, proposal));
        fixture.sql(RUNTIME, """
                INSERT INTO vra.security_event(actor_type,event_code,outcome_code,target_type,risk_code,request_id)
                VALUES ('UNKNOWN','AUTHENTICATION','SUCCEEDED','NONE','NONE',gen_random_uuid())
                """);
        fixture.sql(OWNER, """
                INSERT INTO vra.security_event(actor_type,actor_workload,event_code,outcome_code,target_type,risk_code,
                    source_kind,observer_workload,source_run_id,source_event_digest,source_time,db_operation)
                VALUES ('WORKLOAD','vra_runtime','AUDIT_TAMPER_ATTEMPT','DENIED_42501','PROTECTED_AUDIT',
                    'AUDIT_INTEGRITY_PROBE','POSTGRES_ACL','vra_security_telemetry_observer',gen_random_uuid(),
                    decode(repeat('06',32),'hex'),now(),'UPDATE')
                """);
        String webhookTarget = fixture.rows(OWNER, "INSERT INTO vra.webhook_fixture_target DEFAULT VALUES RETURNING target_id").getFirst().getFirst();
        fixture.sql(RUNTIME, "INSERT INTO vra.webhook_fixture_receipt(event_id,body_digest,target_id,event_kind,status) VALUES ('fixture-event',decode(repeat('07',32),'hex'),'"
                + webhookTarget + "','APPLY','PENDING')");

        for (String table : List.of("account", "organization", "organization_membership", "inventory_operation_grant",
                "poc_account_record", "poc_organization_record", "factor_assertion_use", "staff_role_assignment",
                "security_role_proposal", "webhook_fixture_target")) {
            fixture.denied(OWNER, "UPDATE vra." + table + " SET state='INVALID'", "23514");
        }
        for (String table : List.of("account", "organization_membership", "inventory_operation_grant", "poc_account_record",
                "poc_organization_record", "staff_role_assignment", "security_role_proposal", "webhook_fixture_target")) {
            fixture.denied(OWNER, "UPDATE vra." + table + " SET version=-1", "23514");
        }
        for (var generations : Map.of(
                "account", "auth_generation", "browser_session", "generation,account_auth_generation,csrf_generation",
                "factor_challenge", "session_generation", "factor_assertion_use", "session_generation",
                "security_role_proposal", "maker_session_generation", "security_role_approval", "checker_session_generation").entrySet()) {
            for (String column : generations.getValue().split(",")) fixture.denied(OWNER,
                    "UPDATE vra." + generations.getKey() + " SET " + column + "=-1", "23514");
        }
        for (var digests : Map.of(
                "browser_session", "verifier,csrf_token", "factor_challenge", "context_digest,nonce_digest",
                "factor_assertion_use", "context_digest,jws_digest", "security_role_proposal", "change_digest",
                "security_role_approval", "proposal_digest,context_digest", "security_event", "source_event_digest",
                "webhook_fixture_receipt", "body_digest").entrySet()) {
            for (String column : digests.getValue().split(",")) fixture.denied(OWNER,
                    "UPDATE vra." + digests.getKey() + " SET " + column + "=decode(repeat('00',31),'hex')", "23514");
        }
        fixture.denied(OWNER, "UPDATE vra.inventory_operation_grant SET operation='SELL'", "23514");
        fixture.denied(OWNER, "UPDATE vra.staff_role_assignment SET role_code='SUPERUSER'", "23514");
        fixture.denied(OWNER, "UPDATE vra.staff_role_assignment SET scope_id=NULL", "23502");
        fixture.denied(OWNER, "UPDATE vra.security_role_proposal SET role_code='STAFF_SECURITY_GRANT_CHECKER'", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_role_proposal SET reason='  '", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_role_proposal SET version=2", "23514");
        fixture.denied(OWNER, "UPDATE vra.factor_challenge SET operation='ARBITRARY'", "23514");
        fixture.denied(OWNER, "UPDATE vra.factor_assertion_use SET state='CONSUMED'", "23514");
        fixture.denied(OWNER, "UPDATE vra.factor_challenge SET expires_at=issued_at+interval '91 seconds'", "23514");
        fixture.denied(OWNER, "UPDATE vra.browser_session SET absolute_expires_at=issued_at+interval '3 hours'", "23514");
        fixture.denied(OWNER, "UPDATE vra.browser_session SET idle_expires_at=last_used_at+interval '21 minutes'", "23514");
        fixture.denied(OWNER, "UPDATE vra.browser_session SET assurance_level='MFA',assurance_expires_at=NULL", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_role_proposal SET expires_at=created_at+interval '16 minutes'", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_role_approval SET expires_at=approved_at+interval '6 minutes'", "23514");
        fixture.denied(OWNER, "UPDATE vra.webhook_fixture_receipt SET event_kind='READY',status='PENDING'", "23514");
        fixture.denied(OWNER, "UPDATE vra.webhook_fixture_receipt SET status='IGNORED'", "23514");
        fixture.denied(OWNER, "UPDATE vra.webhook_fixture_receipt SET event_id=E'bad\\nevent'", "23514");
        fixture.denied(OWNER, "UPDATE vra.protected_security_audit SET actor_type='WORKLOAD',actor_workload=NULL", "23514");
        fixture.denied(OWNER, "UPDATE vra.protected_security_audit SET subject_auth_generation_after=2", "23514");
        fixture.denied(OWNER, "UPDATE vra.protected_security_audit SET subject_auth_generation_before=-1", "23514");
        fixture.denied(OWNER, "UPDATE vra.protected_security_audit SET reason=' '", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_event SET actor_type='HUMAN',actor_account_id=NULL", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_event SET source_kind='POSTGRES_ACL' WHERE source_kind='APP'", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_event SET source_kind='APP' WHERE source_kind='POSTGRES_ACL'", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_event SET db_operation='SELECT' WHERE source_kind='POSTGRES_ACL'", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_event SET actor_workload='vra_factor_fixture' WHERE source_kind='POSTGRES_ACL'", "23514");
        fixture.denied(OWNER, "UPDATE vra.security_event SET observer_workload='vra_runtime' WHERE source_kind='POSTGRES_ACL'", "23514");
        fixture.denied(RUNTIME, """
                INSERT INTO vra.security_event(actor_type,actor_workload,event_code,outcome_code,target_type,risk_code,request_id)
                VALUES ('WORKLOAD','vra_runtime','AUDIT_TAMPER_ATTEMPT','DENIED_42501','PROTECTED_AUDIT','AUDIT_INTEGRITY_PROBE',gen_random_uuid())
                """, "23514");
        fixture.denied(RUNTIME, """
                INSERT INTO vra.security_event(actor_type,event_code,outcome_code,target_type,risk_code)
                VALUES ('UNKNOWN','AUTHENTICATION','SUCCEEDED','NONE','NONE')
                """, "23514");
        for (String column : List.of("observer_workload", "source_run_id", "source_event_digest", "source_time", "db_operation")) {
            fixture.denied(OWNER, "UPDATE vra.security_event SET " + column + "=NULL WHERE source_kind='POSTGRES_ACL'", "23514");
        }
        fixture.denied(OWNER, "INSERT INTO vra.external_identity_binding(issuer,subject,account_id) VALUES ('https://issuer.fixture.invalid','subject-a','" + target + "')", "23505");
        fixture.denied(OWNER, "INSERT INTO vra.organization_membership(account_id,org_id) VALUES ('" + maker + "','" + organization + "')", "23505");
        fixture.denied(OWNER, "INSERT INTO vra.inventory_operation_grant(account_id,owner_id,operation) VALUES ('" + maker + "','" + inventoryOwner + "','RESERVE')", "23505");
        fixture.denied(OWNER, """
                INSERT INTO vra.browser_session(verifier,account_id,account_auth_generation,issued_at,last_used_at,
                    idle_expires_at,absolute_expires_at,csrf_token)
                SELECT verifier,account_id,account_auth_generation,issued_at,last_used_at,idle_expires_at,
                    absolute_expires_at,csrf_token FROM vra.browser_session WHERE session_id='%s'
                """.formatted(makerSession), "23505");
        fixture.denied(OWNER, "INSERT INTO vra.factor_assertion_use SELECT * FROM vra.factor_assertion_use", "23505");
        fixture.denied(OWNER, "INSERT INTO vra.security_role_approval SELECT gen_random_uuid(),proposal_id,proposal_digest,proposal_version,checker_account_id,checker_session_id,checker_session_generation,challenge_id,context_digest,approved_at,expires_at,consumed_at FROM vra.security_role_approval", "23505");
        fixture.denied(OWNER, "INSERT INTO vra.security_event SELECT gen_random_uuid(),actor_type,actor_account_id,actor_workload,event_code,outcome_code,occurred_at,request_id,target_type,target_id,target_reference,risk_code,source_kind,observer_workload,source_run_id,source_event_digest,source_time,db_operation FROM vra.security_event WHERE source_kind='POSTGRES_ACL'", "23505");
        fixture.denied(RUNTIME, "INSERT INTO vra.webhook_fixture_receipt SELECT * FROM vra.webhook_fixture_receipt", "23505");
        for (var fk : Map.ofEntries(
                Map.entry("external_identity_binding", "account_id"), Map.entry("organization_membership", "account_id,org_id"),
                Map.entry("inventory_operation_grant", "account_id"), Map.entry("poc_account_record", "owner_account_id"),
                Map.entry("poc_organization_record", "owner_org_id"), Map.entry("browser_session", "account_id"),
                Map.entry("factor_challenge", "actor_account_id,session_id"), Map.entry("factor_assertion_use", "challenge_id"),
                Map.entry("staff_role_assignment", "account_id"), Map.entry("security_role_proposal", "target_account_id,maker_session_id"),
                Map.entry("security_role_approval", "checker_session_id,challenge_id"), Map.entry("protected_security_audit", "subject_account_id"),
                Map.entry("webhook_fixture_receipt", "target_id")).entrySet()) {
            for (String column : fk.getValue().split(",")) fixture.denied(OWNER,
                    "UPDATE vra." + fk.getKey() + " SET " + column + "=gen_random_uuid()", "23503");
        }
        fixture.denied(OWNER, "UPDATE vra.security_role_approval SET proposal_digest=decode(repeat('08',32),'hex'),context_digest=decode(repeat('08',32),'hex')", "23503");
        fixture.denied(OWNER, "UPDATE vra.factor_assertion_use SET context_digest=decode(repeat('08',32),'hex')", "23503");
        fixture.denied(OWNER, "UPDATE vra.poc_account_record SET label=repeat('x',81)", "22001");
        fixture.denied(OWNER, "UPDATE vra.poc_organization_record SET label=repeat('x',81)", "22001");
        for (String table : List.of("poc_account_record", "poc_organization_record")) {
            fixture.sql(RUNTIME, "UPDATE vra." + table + " SET label='allowed fixture label',version=version+1");
            assertEquals("1", fixture.value("SELECT version FROM vra." + table));
        }
        fixture.sql(RUNTIME, "UPDATE vra.webhook_fixture_target SET state='READY',version=version+1");
        fixture.sql(RUNTIME, "UPDATE vra.webhook_fixture_receipt SET status='APPLIED'");
        assertEquals("APPLIED", fixture.value("SELECT status FROM vra.webhook_fixture_receipt"));
    }

    private static String seedSession(Fixture fixture, String account, String verifierByte) throws SQLException {
        return fixture.rows(OWNER, """
                INSERT INTO vra.browser_session(verifier,account_id,account_auth_generation,issued_at,last_used_at,
                    idle_expires_at,absolute_expires_at,csrf_token)
                VALUES (decode(repeat('%s',32),'hex'),'%s',0,now(),now(),now()+interval '20 minutes',
                    now()+interval '2 hours',decode(repeat('10',32),'hex')) RETURNING session_id
                """.formatted(verifierByte, account)).getFirst().getFirst();
    }

    private static String seedChallenge(Fixture fixture, String account, String session, String operation, String contextByte) throws SQLException {
        return fixture.rows(OWNER, """
                INSERT INTO vra.factor_challenge(actor_account_id,session_id,session_generation,operation,
                    context_digest,nonce_digest,issued_at,expires_at)
                VALUES ('%s','%s',0,'%s',decode(repeat('%s',32),'hex'),decode(repeat('11',32),'hex'),
                    now(),now()+interval '90 seconds') RETURNING challenge_id
                """.formatted(account, session, operation, contextByte)).getFirst().getFirst();
    }

    private static void seedPriorState(Fixture fixture) throws Exception {
        UUID sku = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        try (Connection connection = fixture.connection(OWNER); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("INSERT INTO vra.inventory_balance VALUES ('" + sku + "','" + owner + "','" + location + "','AVAILABLE',100,3,7)");
            statement.execute("INSERT INTO vra.inventory_reservation VALUES ('" + reservation + "','" + sku + "','" + owner + "','" + location + "','AVAILABLE',3,now())");
            statement.execute("""
                    INSERT INTO vra.inventory_reservation_idempotency VALUES
                    ('upgrade-fixture','transient',1,repeat('a',64),NULL,NULL,NULL,NULL,now(),NULL),
                    ('upgrade-fixture','success',1,repeat('b',64),'SUCCEEDED','%s',7,NULL,now(),now()),
                    ('upgrade-fixture','rejection',1,repeat('c',64),'REJECTED',NULL,NULL,'INSUFFICIENT_STOCK',now(),now())
                    """.formatted(reservation));
            statement.execute("SET LOCAL ROLE vra_async_executor");
            statement.execute("SELECT vra.async_publish_reservation('" + event + "','" + reservation + "','VALIDATION_EXTERNAL_EFFECT')");
            statement.execute("SET LOCAL ROLE vra_owner");
            statement.execute("INSERT INTO vra.consumer_inbox VALUES ('upgrade-fixture','" + event + "',now())");
            statement.execute("INSERT INTO vra.reservation_projection VALUES ('" + reservation + "','" + sku + "','" + owner + "','" + location + "','AVAILABLE',3,now())");
            statement.execute("""
                    UPDATE vra.outbox_delivery SET state='RECONCILIATION_REQUIRED',failure_class='UNKNOWN_OUTCOME',
                        reason_code='UNKNOWN_EXTERNAL_RESULT',state_changed_at=now() WHERE event_id='%s'
                    """.formatted(event));
            statement.execute("""
                    INSERT INTO vra.reconciliation_case(case_id,event_id,state,external_knowledge,reason_code,
                        next_eligible_at,cycle_claim_limit,created_at,state_changed_at)
                    VALUES ('%s','%s','PENDING','UNKNOWN','UNKNOWN_EXTERNAL_RESULT',now(),4,now(),now())
                    """.formatted(caseId, event));
            statement.execute("""
                    INSERT INTO vra.reconciliation_history(case_id,action_code,to_state,lifetime_attempt,automatic_cycle,
                        cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,recorded_at)
                    VALUES ('%s','CREATED','PENDING',0,1,0,4,'UNKNOWN_EXTERNAL_RESULT','OUTBOX_WORKER',now())
                    """.formatted(caseId));
            connection.commit();
        }
        for (String table : PRIOR_TABLES) {
            assertTrue(Long.parseLong(fixture.value("SELECT count(*) FROM vra." + table)) > 0,
                    "representative accepted state populated: " + table);
        }
    }

    private static Path repositoryRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("validation/poc-04/IMPLEMENTATION_PLAN.md"))) {
            root = root.getParent();
        }
        assertNotNull(root, "repository root containing reviewed plan");
        return root;
    }

    private static final class Fixture implements AutoCloseable {
        private final Map<String, String> passwords = new LinkedHashMap<>();
        private final PostgreSQLContainer database;
        private final Network network;
        private final Map<String, String> labels;
        private final String volumeName;
        private final String networkName;
        private final String containerName;
        private final String declaredEndpoint;
        private String containerId;
        private String networkId;
        private String volumeCreatedAt;
        private String volumeMountpoint;
        private String networkCreatedAt;
        private String containerCreatedAt;
        private boolean volumeCreated;

        private Fixture() throws Exception {
            declaredEndpoint = System.getenv("VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT");
            assertNotNull(declaredEndpoint,
                    "database proof requires an operator-declared dedicated disposable TEST Docker endpoint before start");
            assertTrue(declaredEndpoint.startsWith("unix:///") || declaredEndpoint.startsWith("tcp://"),
                    "TEST endpoint declaration must explicitly identify its Docker endpoint");
            assertEquals(declaredEndpoint, System.getenv("DOCKER_HOST"),
                    "declared disposable TEST endpoint must match execution DOCKER_HOST");
            for (String role : concat(List.of("postgres", MIGRATOR), concat(PRIOR_WORKLOADS, TEST_LOGINS))) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                passwords.put(role, HexFormat.of().formatHex(random));
            }
            assertTrue(Files.readString(repositoryRoot().resolve("validation/poc-04/tooling/image-pins.json"))
                    .contains(POSTGRES_IMAGE), "image must match Phase-0 immutable manifest");
            int ordinal = DATABASE_ORDINAL.getAndIncrement();
            UUID instanceId = uuidV5(RUN_ID, "postgres-authority/" + ordinal);
            String resourcePrefix = "vra-poc04-" + RUN_ID + "-postgres-authority-" + ordinal;
            containerName = resourcePrefix;
            volumeName = resourcePrefix + "-pgdata";
            networkName = resourcePrefix + "-network";
            SourceBinding binding = sourceBinding();
            labels = Map.of(
                    "dev.vra.environment", "TEST", "dev.vra.poc", "04",
                    "dev.vra.run_id", RUN_ID.toString(),
                    "dev.vra.database_instance_id", instanceId.toString(),
                    "dev.vra.source_head", binding.head(),
                    "dev.vra.source_content_sha256", binding.digest(),
                    "dev.vra.tool_manifest_sha256", sha256(repositoryRoot().resolve("validation/poc-04/tooling/tool-manifest.json")));
            network = Network.builder().createNetworkCmdModifier(command -> command.withName(networkName).withLabels(labels)).build();
            database = new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(passwords.get("postgres"))
                    .withNetwork(network).withLabels(labels)
                    .withCreateContainerCmdModifier(command -> {
                        command.withName(containerName);
                        command.getHostConfig().withBinds(new Bind(volumeName,
                                new Volume("/var/lib/postgresql/data"), AccessMode.rw));
                    });
            try {
                // The running container's ImageManifestDescriptor requires Engine API 1.49.
                System.setProperty("api.version", "1.49");
                assertEquals(URI.create(declaredEndpoint), DockerClientFactory.instance().getTransportConfig().getDockerHost(),
                        "selected Docker transport must match the declared dedicated TEST daemon before client initialization");
                var client = database.getDockerClient();
                var daemon = client.infoCmd().exec();
                assertEquals(System.getenv("VRA_POC04_EXPECTED_DAEMON_ID"), daemon.getId(),
                        "actual daemon must match the independently attested disposable TEST daemon");
                assertEquals("linux", daemon.getOsType());
                String platform = switch (daemon.getArchitecture()) {
                    case "arm64", "aarch64" -> "linux/arm64/v8";
                    case "amd64", "x86_64" -> "linux/amd64";
                    default -> throw new AssertionError("unrecorded TEST PostgreSQL execution platform");
                };
                JsonNode selectedPin = verifyPostgresManifestLinkage(platform);
                database.withCreateContainerCmdModifier(command -> command.withPlatform(platform));
                assertThrows(NotFoundException.class, () -> client.inspectContainerCmd(containerName).exec(), "fresh container name absent before creation");
                assertThrows(NotFoundException.class, () -> client.inspectVolumeCmd(volumeName).exec(), "fresh PGDATA volume absent before creation");
                assertThrows(NotFoundException.class, () -> client.inspectNetworkCmd().withNetworkId(networkName).exec(), "fresh network absent before creation");
                client.createVolumeCmd().withName(volumeName).withLabels(labels).exec();
                volumeCreated = true;
                var volume = client.inspectVolumeCmd(volumeName).exec();
                assertEquals(volumeName, volume.getName());
                assertLabels(volume.getLabels());
                volumeCreatedAt = Objects.toString(volume.getRawValues().get("CreatedAt"), "");
                assertTrue(!volumeCreatedAt.isBlank(), "actual volume creation metadata required");
                volumeMountpoint = volume.getMountpoint();
                assertNotNull(volumeMountpoint, "actual volume physical mount identity required");
                networkId = network.getId();
                var actualNetwork = client.inspectNetworkCmd().withNetworkId(networkId).exec();
                assertEquals(networkId, actualNetwork.getId());
                assertEquals(networkName, actualNetwork.getName());
                assertLabels(actualNetwork.getLabels());
                networkCreatedAt = Objects.toString(actualNetwork.getCreated(), "");
                assertTrue(!networkCreatedAt.isBlank(), "actual network creation metadata required");
                database.start();
                containerId = database.getContainerId();
                var inspection = database.getContainerInfo();
                containerCreatedAt = inspection.getCreated();
                assertNotNull(containerCreatedAt, "actual container creation metadata required");
                assertEquals(containerId, inspection.getId(), "actual disposable container identity");
                assertEquals("/" + containerName, inspection.getName());
                assertEquals(POSTGRES_IMAGE, inspection.getConfig().getImage(), "actual immutable execution image");
                for (var label : labels.entrySet()) assertEquals(label.getValue(), inspection.getConfig().getLabels().get(label.getKey()));
                assertTrue(inspection.getNetworkSettings().getNetworks().values().stream()
                        .anyMatch(attached -> network.getId().equals(attached.getNetworkID())), "actual dedicated TEST network");
                Object descriptor = inspection.getRawValues().get("ImageManifestDescriptor");
                assertNotNull(descriptor, "actual running-container platform manifest descriptor required");
                JsonNode actualManifest = JsonMapper.builder().build().valueToTree(descriptor);
                assertEquals("application/vnd.oci.image.manifest.v1+json", actualManifest.path("mediaType").asString());
                assertEquals(selectedPin.path("digest").asString(), actualManifest.path("digest").asString(),
                        "actual running-container platform manifest identity");
                assertEquals(selectedPin.path("response").path("response_bytes").asLong(), actualManifest.path("size").asLong());
                assertEquals(selectedPin.path("platform"), actualManifest.path("platform"),
                        "actual execution platform must exactly match the reviewed selected platform");
                System.out.println("POC04 actual execution platform/manifest PASS: " + platform + " " + actualManifest.path("digest").asString());
                assertTrue(inspection.getMounts().stream().noneMatch(mount -> Objects.toString(mount.getSource(), "").contains("docker.sock")),
                        "TEST PostgreSQL has no Docker socket mount");
                assertEquals(1, inspection.getMounts().size(), "only the recorded fresh PGDATA volume is mounted");
                var pgdata = inspection.getMounts().getFirst();
                assertEquals("volume", pgdata.getRawValues().get("Type"), "actual PGDATA mount type");
                assertEquals(volumeName, pgdata.getName(), "actual PGDATA mount names the recorded run-owned volume");
                assertEquals("/var/lib/postgresql/data", pgdata.getDestination().getPath());
                assertEquals(volumeMountpoint, pgdata.getSource(), "physical volume and mount identity match");
                assertEquals(Boolean.TRUE, pgdata.getRW());
                verifyOwnedResources();
                assertEquals("vra_poc01", value("SELECT current_database()"));
                assertTrue(value("SELECT system_identifier::text FROM pg_control_system()").matches("[0-9]+"),
                        "actual PostgreSQL cluster identity established before bootstrap");
                assertEquals("170011", value("SHOW server_version_num"));
                System.out.println("POC04 PostgreSQL runtime version PASS: server_version_num=170011 (17.11)");
                priorBootstrap("validation/poc-01/db/bootstrap.sql");
                priorBootstrap("validation/poc-03/db/bootstrap-async-roles.sql");
                securityBootstrap();
                for (String role : TEST_LOGINS) {
                    // Generated in this disposable test instance; never read from committed credentials.
                    sql("postgres", "ALTER ROLE " + role + " PASSWORD '" + passwords.get(role) + "'");
                }
                assertEquals("170011", value("SHOW server_version_num"));
            } catch (Throwable failure) {
                try { removeOwnedResources(); } catch (Throwable cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }

        private JsonNode verifyPostgresManifestLinkage(String platform) throws Exception {
            byte[] pins = Files.readAllBytes(repositoryRoot().resolve("validation/poc-04/tooling/image-pins.json"));
            assertEquals("340b51d2910f023eef2f7b8a0a4c4065c4baa8d95b2a95c5aaa6c76b888d123a",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pins)),
                    "frozen Phase-0 image pin bytes must remain unchanged");
            JsonNode postgres = null;
            for (JsonNode record : JsonMapper.builder().build().readTree(pins).path("records")) {
                if (record.path("id").asString().equals("postgresql_test")) postgres = record;
            }
            assertNotNull(postgres, "reviewed PostgreSQL image record required");
            assertEquals(POSTGRES_IMAGE, postgres.path("execution_identifier").asString());
            JsonNode selected = null;
            for (JsonNode candidate : postgres.path("platform_manifests")) {
                JsonNode p = candidate.path("platform");
                String name = p.path("os").asString() + "/" + p.path("architecture").asString()
                        + (p.has("variant") ? "/" + p.path("variant").asString() : "");
                if (platform.equals(name)) {
                    assertTrue(selected == null, "exactly one reviewed record for the selected platform");
                    selected = candidate;
                }
            }
            assertNotNull(selected, "unknown/unrecorded platforms fail closed");
            JsonNode index = immutableManifest(postgres.path("index_digest").asString(),
                    postgres.path("index_response").path("response_bytes").asInt());
            assertEquals("application/vnd.oci.image.index.v1+json", index.path("mediaType").asString());
            int matches = 0;
            for (JsonNode candidate : index.path("manifests")) {
                if (selected.path("platform").equals(candidate.path("platform"))) {
                    matches++;
                    assertEquals(selected.path("digest").asString(), candidate.path("digest").asString());
                    assertEquals("application/vnd.oci.image.manifest.v1+json", candidate.path("mediaType").asString());
                    assertEquals(selected.path("response").path("response_bytes").asLong(), candidate.path("size").asLong());
                }
            }
            assertEquals(1, matches, "exact index-to-platform-manifest linkage");
            JsonNode manifest = immutableManifest(selected.path("digest").asString(),
                    selected.path("response").path("response_bytes").asInt());
            assertEquals("application/vnd.oci.image.manifest.v1+json", manifest.path("mediaType").asString());
            JsonNode config = manifest.path("config");
            assertEquals("application/vnd.oci.image.config.v1+json", config.path("mediaType").asString());
            assertEquals(selected.path("config_digest").asString(), config.path("digest").asString(),
                    "config identity is the digest linked by the exact immutable platform manifest, never Docker image Id");
            assertEquals(selected.path("config_response").path("response_bytes").asLong(), config.path("size").asLong());
            System.out.println("POC04 index/platform/manifest/config linkage PASS: " + POSTGRES_IMAGE + " "
                    + platform + " " + selected.path("digest").asString() + " " + config.path("digest").asString());
            return selected;
        }

        private JsonNode immutableManifest(String digest, int expectedBytes) throws Exception {
            ProcessBuilder inspection = new ProcessBuilder("docker", "buildx", "imagetools", "inspect",
                    "docker.io/library/postgres@" + digest, "--raw")
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            inspection.environment().put("DOCKER_HOST", declaredEndpoint);
            Process process = inspection.start();
            CompletableFuture<byte[]> output = new CompletableFuture<>();
            Thread.startVirtualThread(() -> {
                try { output.complete(process.getInputStream().readNBytes(16_385)); }
                catch (Exception failure) { output.completeExceptionally(failure); }
            });
            try {
                byte[] raw = output.get(45, TimeUnit.SECONDS);
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "immutable manifest inspection must terminate");
                assertEquals(0, process.exitValue(), "immutable manifest inspection must succeed");
                assertEquals(expectedBytes, raw.length, "exact immutable manifest response length; no normalization");
                assertEquals(digest, "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)),
                        "raw immutable manifest bytes must hash to the reviewed digest");
                JsonNode manifest = JsonMapper.builder().build().readTree(raw);
                assertEquals(2, manifest.path("schemaVersion").asInt());
                return manifest;
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
        }

        private void priorBootstrap(String path) throws Exception {
            database.copyFileToContainer(MountableFile.forHostPath(repositoryRoot().resolve(path)), "/tmp/poc04-bootstrap.sql");
            List<String> command = new ArrayList<>(List.of("psql", "-U", "postgres", "-d", "vra_poc01", "-v", "ON_ERROR_STOP=1"));
            Map<String, String> variables = Map.of(
                    "migrator_password", MIGRATOR, "runtime_password", RUNTIME,
                    "outbox_worker_password", "vra_outbox_worker",
                    "reconciliation_worker_password", "vra_reconciliation_worker",
                    "async_operator_password", "vra_async_operator",
                    "projection_rebuilder_password", "vra_projection_rebuilder",
                    "async_observer_password", "vra_async_observer");
            for (var variable : variables.entrySet()) {
                command.add("-v");
                command.add(variable.getKey() + "=" + passwords.get(variable.getValue()));
            }
            command.addAll(List.of("-f", "/tmp/poc04-bootstrap.sql"));
            var result = database.execInContainer(command.toArray(String[]::new));
            assertEquals(0, result.getExitCode(), "historical admin bootstrap failed: " + path);
        }

        private void securityBootstrap() throws SQLException, java.io.IOException {
            sql("postgres", Files.readString(repositoryRoot().resolve("validation/poc-04/db/bootstrap-security-roles.sql")));
        }

        private Flyway flyway(String target) {
            var configuration = Flyway.configure().dataSource(new OwnerRoleDataSource(
                            database.getJdbcUrl(), MIGRATOR, passwords.get(MIGRATOR)))
                    .locations("classpath:db/migration").schemas("vra").defaultSchema("vra").createSchemas(false);
            if (target != null) configuration.target(target);
            return configuration.load();
        }

        private Connection connection(String role) throws SQLException {
            if (OWNER.equals(role) || EXECUTORS.contains(role)) {
                Connection connection = new OwnerRoleDataSource(database.getJdbcUrl(), MIGRATOR, passwords.get(MIGRATOR)).getConnection();
                if (EXECUTORS.contains(role)) {
                    try (Statement statement = connection.createStatement()) { statement.execute("SET ROLE " + role); }
                }
                return connection;
            }
            return DriverManager.getConnection(database.getJdbcUrl(), role, passwords.get(role));
        }

        private void sql(String role, String sql) throws SQLException {
            try (Connection connection = connection(role); Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        private List<List<String>> rows(String role, String sql) throws SQLException {
            try (Connection connection = connection(role); Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(sql)) {
                List<List<String>> rows = new ArrayList<>();
                while (result.next()) {
                    List<String> row = new ArrayList<>();
                    for (int index = 1; index <= result.getMetaData().getColumnCount(); index++) {
                        row.add(Objects.toString(result.getString(index), "<null>"));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }

        private String value(String sql) throws SQLException { return rows("postgres", sql).getFirst().getFirst(); }
        private List<String> column(String sql) throws SQLException { return rows("postgres", sql).stream().map(List::getFirst).toList(); }

        private void denied(String role, String sql, String state) throws SQLException {
            Map<String, String> before = snapshot(allTables());
            SQLException failure = assertThrows(SQLException.class, () -> sql(role, sql), "SQL denial under " + role);
            assertEquals(state, failure.getSQLState(), "SQLSTATE under " + role);
            assertEquals(before, snapshot(allTables()), "denied SQL leaves all authoritative rows unchanged");
        }

        private Map<String, String> snapshot(List<String> tables) throws SQLException {
            Map<String, String> snapshot = new LinkedHashMap<>();
            String sql = tables.stream().map(table -> "SELECT '" + table + "',encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(t) "
                    + "ORDER BY to_jsonb(t)::text)::text,'[]'),'UTF8')),'hex') FROM vra." + table + " t")
                    .collect(Collectors.joining(" UNION ALL "));
            for (List<String> row : rows("postgres", sql)) snapshot.put(row.getFirst(), row.get(1));
            return snapshot;
        }

        private List<List<String>> roleState() throws SQLException { return roleStateExcluding(""); }

        private List<List<String>> roleStateExcluding(String excluded) throws SQLException {
            List<List<String>> result = rows("postgres", "SELECT rolname,rolcanlogin,rolinherit,rolsuper,rolcreatedb,rolcreaterole,"
                    + "rolreplication,rolbypassrls FROM pg_roles WHERE rolname LIKE 'vra_%' AND rolname<>'" + excluded + "' ORDER BY rolname");
            result.addAll(rows("postgres", """
                    SELECT member.rolname,parent.rolname,m.set_option,m.inherit_option,m.admin_option
                    FROM pg_auth_members m JOIN pg_roles member ON member.oid=m.member
                    JOIN pg_roles parent ON parent.oid=m.roleid
                    WHERE (member.rolname LIKE 'vra_%%' OR parent.rolname LIKE 'vra_%%')
                    AND member.rolname<>'%s' AND parent.rolname<>'%s' ORDER BY member.rolname,parent.rolname
                    """.formatted(excluded, excluded)));
            return result;
        }

        private List<List<String>> priorCatalog() throws SQLException {
            String names = PRIOR_TABLES.stream().map(name -> "'" + name + "'").collect(Collectors.joining(","));
            return rows("postgres", "SELECT c.relname,pg_get_userbyid(c.relowner),c.relacl::text,a.attname,a.attacl::text "
                    + "FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped "
                    + "WHERE c.relnamespace='vra'::regnamespace AND c.relname IN (" + names + ") ORDER BY c.relname,a.attnum");
        }

        private List<List<String>> catalog() throws SQLException {
            List<List<String>> catalog = rows("postgres", """
                    SELECT c.relname,c.relkind,pg_get_userbyid(c.relowner),c.relacl::text,
                        a.attname,format_type(a.atttypid,a.atttypmod),a.attnotnull,a.attacl::text
                    FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
                    WHERE c.relnamespace='vra'::regnamespace ORDER BY c.relname,a.attnum
                    """);
            catalog.addAll(rows("postgres", """
                    SELECT conname,conrelid::regclass::text,pg_get_constraintdef(oid)
                    FROM pg_constraint WHERE connamespace='vra'::regnamespace ORDER BY conrelid,conname
                    """));
            catalog.addAll(rows("postgres", "SELECT indexname,indexdef FROM pg_indexes WHERE schemaname='vra' ORDER BY indexname"));
            return catalog;
        }

        @Override
        public void close() {
            removeOwnedResources();
        }

        private void assertLabels(Map<String, String> actual) {
            assertNotNull(actual, "physical TEST resource labels required");
            for (var label : labels.entrySet()) assertEquals(label.getValue(), actual.get(label.getKey()), "physical TEST ownership label: " + label.getKey());
        }

        private void verifyOwnedResources() {
            assertEquals(declaredEndpoint, System.getenv("VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT"));
            assertEquals(declaredEndpoint, System.getenv("DOCKER_HOST"), "dedicated daemon declaration must remain unchanged");
            assertEquals(URI.create(declaredEndpoint), DockerClientFactory.instance().getTransportConfig().getDockerHost(),
                    "selected Docker transport must remain the declared dedicated TEST daemon before client initialization");
            var client = database.getDockerClient();
            if (containerId != null) {
                var actualContainer = client.inspectContainerCmd(containerId).exec();
                assertEquals(containerId, actualContainer.getId());
                assertEquals("/" + containerName, actualContainer.getName());
                assertLabels(actualContainer.getConfig().getLabels());
                assertEquals(containerCreatedAt, actualContainer.getCreated(), "container identity has not been substituted");
            }
            if (networkId != null) {
                var actualNetwork = client.inspectNetworkCmd().withNetworkId(networkId).exec();
                assertEquals(networkId, actualNetwork.getId());
                assertEquals(networkName, actualNetwork.getName());
                assertLabels(actualNetwork.getLabels());
                assertEquals(networkCreatedAt, Objects.toString(actualNetwork.getCreated(), ""));
            }
            if (volumeCreated) {
                var actualVolume = client.inspectVolumeCmd(volumeName).exec();
                assertEquals(volumeName, actualVolume.getName());
                assertLabels(actualVolume.getLabels());
                assertEquals(volumeCreatedAt, Objects.toString(actualVolume.getRawValues().get("CreatedAt"), ""));
                assertEquals(volumeMountpoint, actualVolume.getMountpoint(), "volume physical identity has not been substituted");
            }
        }

        private void removeOwnedResources() {
            // Reinspect first. Wrong IDs/labels stop teardown, preserving unknown resources for operator review.
            verifyOwnedResources();
            var client = database.getDockerClient();
            if (containerId != null) {
                database.stop();
                assertThrows(NotFoundException.class, () -> client.inspectContainerCmd(containerId).exec(), "recorded container removed");
            }
            if (networkId != null) {
                network.close();
                assertThrows(NotFoundException.class, () -> client.inspectNetworkCmd().withNetworkId(networkId).exec(), "recorded network removed");
            }
            if (volumeCreated) {
                client.removeVolumeCmd(volumeName).exec();
                assertThrows(NotFoundException.class, () -> client.inspectVolumeCmd(volumeName).exec(), "recorded PGDATA volume removed");
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private record SourceBinding(String head, String digest, int files) { }

    private static SourceBinding sourceBinding() throws Exception {
        // Reuse historical canonical encoding without rewriting Phase-0 evidence. Add newly authorized backend source.
        String script = """
                import hashlib, importlib.util, json
                from pathlib import Path
                root = Path.cwd()
                spec = importlib.util.spec_from_file_location('historical_binding', root / 'validation/poc-04/scripts/source-manifest.py')
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                manifest = module.manifest()
                files = {f['path']: f for f in manifest['files']}
                others = module.git('ls-files', '--others', '--exclude-standard', '-z').decode().split('\\0')
                for name in sorted(p for p in others if p.startswith('backend/')):
                    relative = Path(name)
                    if module.EXCLUDED_PARTS.intersection(relative.parts):
                        continue
                    if relative.suffix.lower() in module.SECRET_SUFFIXES or relative.name.startswith('.env'):
                        raise ValueError('secret-material path in backend source scope')
                    path = root / relative
                    if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
                        raise ValueError('missing/nonregular/escaping backend source')
                    data = path.read_bytes()
                    files[name] = {'path': name, 'sha256': hashlib.sha256(data).hexdigest(), 'size_bytes': len(data),
                                   'mode': format(path.stat().st_mode & 0o777, '04o'), 'tracked': False}
                for required in ('backend/migration/src/main/resources/db/migration/V4__poc04_identity_security_foundation.sql',
                                 'backend/migration/src/test/java/dev/vra/migration/Poc04FoundationMigrationIntegrationTest.java'):
                    if required not in files:
                        raise ValueError('authorized Phase-1 backend source missing from binding')
                for item in files.values():
                    path = root / item['path']
                    if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
                        raise ValueError('source changed during collection')
                    if format(path.stat().st_mode & 0o777, '04o') != item['mode']:
                        raise ValueError('source mode changed during collection')
                if module.git('rev-parse', 'HEAD').decode().strip() != manifest['head']:
                    raise ValueError('HEAD changed during collection')
                if module.git('status', '--porcelain=v1', '-uall').decode().splitlines() != manifest['worktree']['porcelain_v1']:
                    raise ValueError('source status changed during collection')
                content = [{k: files[p][k] for k in ('path','sha256','size_bytes','mode')} for p in sorted(files)]
                digest = hashlib.sha256(json.dumps(content,sort_keys=True,separators=(',',':')).encode()).hexdigest()
                print(manifest['head'])
                print(digest)
                print(len(files))
                """;
        Process process = new ProcessBuilder("python3", "-B", "-c", script).directory(repositoryRoot().toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), "complete source binding must succeed before disposable TEST provisioning");
        List<String> fields = output.lines().toList();
        assertEquals(3, fields.size());
        assertTrue(fields.get(0).matches("[0-9a-f]{40}"));
        assertTrue(fields.get(1).matches("[0-9a-f]{64}"));
        return new SourceBinding(fields.get(0), fields.get(1), Integer.parseInt(fields.get(2)));
    }

    private static UUID uuidV5(UUID namespace, String logicalName) throws Exception {
        // SHA-1 is required by UUIDv5 identity derivation; it is never used for secret/digest verification.
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        var bytes = java.nio.ByteBuffer.allocate(16).putLong(namespace.getMostSignificantBits()).putLong(namespace.getLeastSignificantBits());
        digest.update(bytes.array());
        byte[] hash = digest.digest(logicalName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
        var result = java.nio.ByteBuffer.wrap(hash);
        return new UUID(result.getLong(), result.getLong());
    }
}
