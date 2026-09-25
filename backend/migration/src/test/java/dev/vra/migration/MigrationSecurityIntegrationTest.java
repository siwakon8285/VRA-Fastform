package dev.vra.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
class MigrationSecurityIntegrationTest {

    private static final String DATABASE = "vra_poc01";
    private static final String ADMIN_USER = "postgres";
    private static final String ADMIN_PASSWORD = "poc01-admin-test-only";

    private static final String MIGRATOR_USER = "vra_migrator";
    private static final String MIGRATOR_PASSWORD = "poc01-migrator-test-only";

    private static final String RUNTIME_USER = "vra_runtime";
    private static final String RUNTIME_PASSWORD = "poc01-runtime-test-only";

    @Test
    void provesMigrationOwnershipAndRuntimeLeastPrivilege() throws Exception {
        PostgreSQLContainer postgres =
                new PostgreSQLContainer("postgres:17.11")
                        .withDatabaseName(DATABASE)
                        .withUsername(ADMIN_USER)
                        .withPassword(ADMIN_PASSWORD);

        postgres.start();

        try {
            bootstrapRolesAndSchema(postgres);
            verifyRoleAttributes(postgres);
            verifyMigratorMembership(postgres);

            assertInsufficientPrivilege(
                    () -> executeAs(
                            postgres,
                            MIGRATOR_USER,
                            MIGRATOR_PASSWORD,
                            "CREATE TABLE vra.migrator_without_set_role (id BIGINT)"
                    )
            );

            MigrationRunner migrationRunner = new MigrationRunner();

            assertEquals(
                    2,
                    migrationRunner.migrate(
                            postgres.getJdbcUrl(),
                            MIGRATOR_USER,
                            MIGRATOR_PASSWORD
                    )
            );

            assertEquals(
                    0,
                    migrationRunner.migrate(
                            postgres.getJdbcUrl(),
                            MIGRATOR_USER,
                            MIGRATOR_PASSWORD
                    )
            );

            migrationRunner.validate(
                    postgres.getJdbcUrl(),
                    MIGRATOR_USER,
                    MIGRATOR_PASSWORD
            );

            verifyTableOwner(postgres, "inventory_balance", "vra_owner");
            verifyTableOwner(postgres, "inventory_reservation", "vra_owner");
            verifyTableOwner(postgres, "flyway_schema_history", "vra_owner");

            verifyTableOwner(postgres, "inventory_reservation_idempotency", "vra_owner");
            verifyRuntimeDmlAndDdlBoundary(postgres);
            verifyIdempotencyConstraints(postgres);
            verifyIdempotencyRuntimePrivileges(postgres);

            assertThrows(
                    FlywayException.class,
                    () -> migrationRunner.migrate(
                            postgres.getJdbcUrl(),
                            RUNTIME_USER,
                            RUNTIME_PASSWORD
                    )
            );

            corruptMigrationChecksum(postgres);

            assertThrows(
                    FlywayException.class,
                    () -> migrationRunner.validate(
                            postgres.getJdbcUrl(),
                            MIGRATOR_USER,
                            MIGRATOR_PASSWORD
                    )
            );
        } finally {
            postgres.stop();
        }
    }

    @Test
    void upgradesActualVersionOneDatabaseToVersionTwo() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11")
                .withDatabaseName(DATABASE)
                .withUsername(ADMIN_USER)
                .withPassword(ADMIN_PASSWORD)) {
            postgres.start();
            bootstrapRolesAndSchema(postgres);
            Flyway versionOne = Flyway.configure()
                    .dataSource(new OwnerRoleDataSource(
                            postgres.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD))
                    .locations("classpath:db/migration")
                    .schemas("vra")
                    .defaultSchema("vra")
                    .createSchemas(false)
                    .target("1")
                    .load();
            assertEquals(1, versionOne.migrate().migrationsExecuted);
            versionOne.validate();
            verifyTableOwner(postgres, "inventory_balance", "vra_owner");
            verifyTableOwner(postgres, "inventory_reservation", "vra_owner");
            try (Connection connection = adminConnection(postgres);
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT to_regclass('vra.inventory_reservation_idempotency') IS NULL")) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1), "V2 table must not exist at version 1");
            }

            MigrationRunner runner = new MigrationRunner();
            assertEquals(1, runner.migrate(
                    postgres.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD));
            runner.validate(postgres.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD);
            verifyTableOwner(postgres, "inventory_reservation_idempotency", "vra_owner");
            assertEquals(0, runner.migrate(
                    postgres.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD));
        }
    }

    private static void verifyIdempotencyConstraints(PostgreSQLContainer postgres)
            throws SQLException {
        UUID sku = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        seedAvailableBalanceAsOwner(postgres, sku, owner, location);
        try (Connection connection = new OwnerRoleDataSource(
                postgres.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD).getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO vra.inventory_reservation
                        (reservation_id, sku_id, owner_id, location_id,
                         stock_status, quantity, created_at)
                    VALUES (?, ?, ?, ?, 'AVAILABLE', 1, CURRENT_TIMESTAMP)
                    """)) {
                insert.setObject(1, reservation);
                insert.setObject(2, sku);
                insert.setObject(3, owner);
                insert.setObject(4, location);
                assertEquals(1, insert.executeUpdate());
            }
            OffsetDateTime completed = OffsetDateTime.now();
            String fingerprint = "a".repeat(64);
            insertIdempotency(connection, "transient", 1, fingerprint,
                    null, null, null, null, null);
            insertIdempotency(connection, "success", 1, fingerprint,
                    "SUCCEEDED", reservation, 1L, null, completed);
            insertIdempotency(connection, "rejected", 1, fingerprint,
                    "REJECTED", null, null, "INSUFFICIENT_STOCK", completed);

            assertSqlState("23514", "unsupported fingerprint version",
                    () -> insertIdempotency(connection, "bad-version", 2, fingerprint,
                            null, null, null, null, null));
            for (String invalidFingerprint : new String[]{
                    "A".repeat(64), "a".repeat(63), "g".repeat(64)}) {
                assertSqlState("23514", "invalid fingerprint: " + invalidFingerprint,
                        () -> insertIdempotency(connection, "bad-fingerprint", 1,
                                invalidFingerprint, null, null, null, null, null));
            }
            assertSqlState("22001", "fingerprint exceeds column length",
                    () -> insertIdempotency(connection, "long-fingerprint", 1, "a".repeat(65),
                            null, null, null, null, null));
            assertSqlState("23514", "invalid outcome",
                    () -> insertIdempotency(connection, "bad-outcome", 1, fingerprint,
                            "UNKNOWN", null, null, null, null));
            assertSqlState("23514", "NULL outcome with success payload",
                    () -> insertIdempotency(connection, "null-success", 1, fingerprint,
                            null, reservation, 1L, null, completed));
            assertSqlState("23514", "NULL outcome with rejection payload",
                    () -> insertIdempotency(connection, "null-rejection", 1, fingerprint,
                            null, null, null, "INSUFFICIENT_STOCK", completed));
            assertSqlState("23514", "success missing reservation",
                    () -> insertIdempotency(connection, "missing-reservation", 1, fingerprint,
                            "SUCCEEDED", null, 1L, null, completed));
            assertSqlState("23514", "success missing version",
                    () -> insertIdempotency(connection, "missing-version", 1, fingerprint,
                            "SUCCEEDED", reservation, null, null, completed));
            assertSqlState("23514", "success missing completion",
                    () -> insertIdempotency(connection, "missing-success-completion", 1, fingerprint,
                            "SUCCEEDED", reservation, 1L, null, null));
            assertSqlState("23514", "success carrying rejection",
                    () -> insertIdempotency(connection, "mixed-success", 1, fingerprint,
                            "SUCCEEDED", reservation, 1L, "INSUFFICIENT_STOCK", completed));
            assertSqlState("23514", "rejection carrying reservation",
                    () -> insertIdempotency(connection, "rejection-reservation", 1, fingerprint,
                            "REJECTED", reservation, null, "INSUFFICIENT_STOCK", completed));
            assertSqlState("23514", "rejection carrying version",
                    () -> insertIdempotency(connection, "rejection-version", 1, fingerprint,
                            "REJECTED", null, 1L, "INSUFFICIENT_STOCK", completed));
            assertSqlState("23514", "rejection missing code",
                    () -> insertIdempotency(connection, "missing-code", 1, fingerprint,
                            "REJECTED", null, null, null, completed));
            assertSqlState("23514", "rejection missing completion",
                    () -> insertIdempotency(connection, "missing-rejection-completion", 1, fingerprint,
                            "REJECTED", null, null, "INSUFFICIENT_STOCK", null));
            assertSqlState("23503", "reservation foreign key",
                    () -> insertIdempotency(connection, "unknown-reservation", 1, fingerprint,
                            "SUCCEEDED", UUID.randomUUID(), 1L, null, completed));
            assertSqlState("23505", "duplicate actor/key",
                    () -> insertIdempotency(connection, "transient", 1, fingerprint,
                            null, null, null, null, null));
        }
    }

    private static void insertIdempotency(
            Connection connection, String key, int fingerprintVersion, String fingerprint,
            String outcome, UUID reservation, Long version, String rejection,
            OffsetDateTime completed
    ) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO vra.inventory_reservation_idempotency (
                    actor_scope, idempotency_key, fingerprint_version, request_fingerprint,
                    outcome_status, reservation_id, inventory_version, rejection_code,
                    created_at, completed_at
                )
                VALUES ('test-actor', ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """)) {
            insert.setString(1, key);
            insert.setInt(2, fingerprintVersion);
            insert.setString(3, fingerprint);
            insert.setString(4, outcome);
            insert.setObject(5, reservation);
            insert.setObject(6, version);
            insert.setString(7, rejection);
            insert.setObject(8, completed);
            assertEquals(1, insert.executeUpdate());
        }
    }

    private static void verifyIdempotencyRuntimePrivileges(PostgreSQLContainer postgres)
            throws SQLException {
        try (Connection runtime = roleConnection(postgres, RUNTIME_USER, RUNTIME_PASSWORD)) {
            insertIdempotency(runtime, "runtime-claim", 1, "b".repeat(64),
                    null, null, null, null, null);
            try (Statement statement = runtime.createStatement()) {
                try (ResultSet result = statement.executeQuery("""
                        SELECT outcome_status IS NULL
                        FROM vra.inventory_reservation_idempotency
                        WHERE actor_scope = 'test-actor' AND idempotency_key = 'runtime-claim'
                        """)) {
                    assertTrue(result.next());
                    assertTrue(result.getBoolean(1));
                    assertFalse(result.next());
                }
                assertEquals(1, statement.executeUpdate("""
                        UPDATE vra.inventory_reservation_idempotency
                        SET outcome_status = 'REJECTED',
                            rejection_code = 'INSUFFICIENT_STOCK',
                            completed_at = CURRENT_TIMESTAMP
                        WHERE actor_scope = 'test-actor' AND idempotency_key = 'runtime-claim'
                        """));
                try (ResultSet result = statement.executeQuery("""
                        SELECT outcome_status, rejection_code, completed_at IS NOT NULL
                        FROM vra.inventory_reservation_idempotency
                        WHERE actor_scope = 'test-actor' AND idempotency_key = 'runtime-claim'
                        """)) {
                    assertTrue(result.next());
                    assertEquals("REJECTED", result.getString(1));
                    assertEquals("INSUFFICIENT_STOCK", result.getString(2));
                    assertTrue(result.getBoolean(3));
                }
                try (ResultSet result = statement.executeQuery(
                        "SELECT pg_has_role(current_user, 'vra_owner', 'MEMBER')")) {
                    assertTrue(result.next());
                    assertFalse(result.getBoolean(1), "runtime must not be an owner member");
                }
            }
            for (String privilege : new String[]{
                    "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER"}) {
                try (PreparedStatement statement = runtime.prepareStatement("""
                        SELECT has_table_privilege(
                            current_user, 'vra.inventory_reservation_idempotency', ?)
                        """)) {
                    statement.setString(1, privilege);
                    try (ResultSet result = statement.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(
                                privilege.equals("SELECT") || privilege.equals("INSERT")
                                        || privilege.equals("UPDATE"),
                                result.getBoolean(1), privilege);
                    }
                }
            }
        }
        assertInsufficientPrivilege(() -> executeAs(postgres, RUNTIME_USER, RUNTIME_PASSWORD,
                "DELETE FROM vra.inventory_reservation_idempotency"));
        assertInsufficientPrivilege(() -> executeAs(postgres, RUNTIME_USER, RUNTIME_PASSWORD,
                "TRUNCATE vra.inventory_reservation_idempotency"));
    }

    private static void assertSqlState(String expected, String scenario, SqlAction action) {
        SQLException error = assertThrows(SQLException.class, action::run, scenario);
        assertEquals(expected, error.getSQLState(), scenario);
    }

    private static void bootstrapRolesAndSchema(PostgreSQLContainer postgres)
            throws SQLException {
        try (Connection connection = adminConnection(postgres);
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE ROLE vra_owner
                    NOLOGIN
                    NOSUPERUSER
                    NOCREATEDB
                    NOCREATEROLE
                    NOREPLICATION
                    NOBYPASSRLS
                    """);

            statement.execute("""
                    CREATE ROLE vra_migrator
                    LOGIN
                    NOINHERIT
                    NOSUPERUSER
                    NOCREATEDB
                    NOCREATEROLE
                    NOREPLICATION
                    NOBYPASSRLS
                    PASSWORD 'poc01-migrator-test-only'
                    """);

            statement.execute("""
                    CREATE ROLE vra_runtime
                    LOGIN
                    NOINHERIT
                    NOSUPERUSER
                    NOCREATEDB
                    NOCREATEROLE
                    NOREPLICATION
                    NOBYPASSRLS
                    PASSWORD 'poc01-runtime-test-only'
                    """);

            statement.execute("""
                    GRANT vra_owner TO vra_migrator
                    WITH ADMIN FALSE, INHERIT FALSE, SET TRUE
                    """);

            statement.execute(
                    "REVOKE CREATE ON DATABASE " + quoteIdentifier(DATABASE) + " FROM PUBLIC"
            );

            statement.execute(
                    "GRANT CONNECT ON DATABASE " + quoteIdentifier(DATABASE)
                            + " TO vra_migrator, vra_runtime"
            );

            statement.execute("CREATE SCHEMA vra AUTHORIZATION vra_owner");
            statement.execute("GRANT USAGE ON SCHEMA vra TO vra_runtime");
        }
    }

    private static void verifyRoleAttributes(PostgreSQLContainer postgres)
            throws SQLException {
        try (Connection connection = adminConnection(postgres);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         rolcanlogin,
                         rolinherit,
                         rolreplication,
                         rolsuper,
                         rolcreatedb,
                         rolcreaterole,
                         rolbypassrls
                     FROM pg_roles
                     WHERE rolname = ?
                     """)) {

            verifyRole(statement, "vra_owner", false);
            verifyRole(statement, MIGRATOR_USER, true);
            verifyRole(statement, RUNTIME_USER, true);
        }
    }

    private static void verifyRole(PreparedStatement statement, String role, boolean canLogin)
            throws SQLException {
        statement.setString(1, role);

        try (ResultSet result = statement.executeQuery()) {
            assertTrue(result.next(), "role must exist: " + role);
            assertEquals(canLogin, result.getBoolean("rolcanlogin"));
            assertFalse(result.getBoolean("rolsuper"));
            assertFalse(result.getBoolean("rolreplication"));
            if (!role.equals("vra_owner")) {
                assertFalse(result.getBoolean("rolinherit"));
            }
            assertFalse(result.getBoolean("rolcreatedb"));
            assertFalse(result.getBoolean("rolcreaterole"));
            assertFalse(result.getBoolean("rolbypassrls"));
            assertFalse(result.next(), "role must be unique: " + role);
        }
    }

    private static void verifyMigratorMembership(PostgreSQLContainer postgres)
            throws SQLException {
        try (Connection connection = adminConnection(postgres);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         membership.admin_option,
                         membership.inherit_option,
                         membership.set_option
                     FROM pg_auth_members membership
                     JOIN pg_roles granted_role
                       ON granted_role.oid = membership.roleid
                     JOIN pg_roles member_role
                       ON member_role.oid = membership.member
                     WHERE granted_role.rolname = 'vra_owner'
                       AND member_role.rolname = 'vra_migrator'
                     """);
             ResultSet result = statement.executeQuery()) {

            assertTrue(result.next(), "migrator membership must exist");
            assertFalse(result.getBoolean("admin_option"));
            assertFalse(result.getBoolean("inherit_option"));
            assertTrue(result.getBoolean("set_option"));
            assertFalse(result.next(), "membership must be unique");
        }
    }

    private static void verifyTableOwner(
            PostgreSQLContainer postgres,
            String table,
            String expectedOwner
    ) throws SQLException {
        try (Connection connection = adminConnection(postgres);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT tableowner
                     FROM pg_tables
                     WHERE schemaname = 'vra'
                       AND tablename = ?
                     """)) {

            statement.setString(1, table);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "table must exist: " + table);
                assertEquals(expectedOwner, result.getString("tableowner"));
                assertFalse(result.next(), "table must be unique: " + table);
            }
        }
    }

    private static void verifyRuntimeDmlAndDdlBoundary(PostgreSQLContainer postgres)
            throws SQLException {
        UUID skuId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        seedAvailableBalanceAsOwner(postgres, skuId, ownerId, locationId);

        try (Connection runtime = roleConnection(
                postgres,
                RUNTIME_USER,
                RUNTIME_PASSWORD
        )) {
            try (PreparedStatement select = runtime.prepareStatement("""
                    SELECT on_hand, reserved, version
                    FROM vra.inventory_balance
                    WHERE sku_id = ?
                      AND owner_id = ?
                      AND location_id = ?
                      AND stock_status = 'AVAILABLE'
                    """)) {

                select.setObject(1, skuId);
                select.setObject(2, ownerId);
                select.setObject(3, locationId);

                try (ResultSet result = select.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(10L, result.getLong("on_hand"));
                    assertEquals(0L, result.getLong("reserved"));
                    assertEquals(0L, result.getLong("version"));
                }
            }

            try (PreparedStatement update = runtime.prepareStatement("""
                    UPDATE vra.inventory_balance
                    SET reserved = reserved + 1,
                        version = version + 1
                    WHERE sku_id = ?
                      AND owner_id = ?
                      AND location_id = ?
                      AND stock_status = 'AVAILABLE'
                    """)) {

                update.setObject(1, skuId);
                update.setObject(2, ownerId);
                update.setObject(3, locationId);

                assertEquals(1, update.executeUpdate());
            }

            try (PreparedStatement insert = runtime.prepareStatement("""
                    INSERT INTO vra.inventory_reservation (
                        reservation_id,
                        sku_id,
                        owner_id,
                        location_id,
                        stock_status,
                        quantity,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, 'AVAILABLE', ?, ?)
                    """)) {

                insert.setObject(1, reservationId);
                insert.setObject(2, skuId);
                insert.setObject(3, ownerId);
                insert.setObject(4, locationId);
                insert.setLong(5, 1L);
                insert.setObject(6, OffsetDateTime.now());

                assertEquals(1, insert.executeUpdate());
            }

            try (PreparedStatement select = runtime.prepareStatement("""
                    SELECT quantity
                    FROM vra.inventory_reservation
                    WHERE reservation_id = ?
                    """)) {

                select.setObject(1, reservationId);

                try (ResultSet result = select.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1L, result.getLong("quantity"));
                }
            }
        }

        assertInsufficientPrivilege(
                () -> executeAs(
                        postgres,
                        RUNTIME_USER,
                        RUNTIME_PASSWORD,
                        """
                        INSERT INTO vra.inventory_balance (
                            sku_id,
                            owner_id,
                            location_id,
                            stock_status,
                            on_hand,
                            reserved,
                            version
                        )
                        VALUES (
                            gen_random_uuid(),
                            gen_random_uuid(),
                            gen_random_uuid(),
                            'AVAILABLE',
                            1,
                            0,
                            0
                        )
                        """
                )
        );

        assertInsufficientPrivilege(
                () -> executeAs(
                        postgres,
                        RUNTIME_USER,
                        RUNTIME_PASSWORD,
                        "DELETE FROM vra.inventory_reservation"
                )
        );

        assertInsufficientPrivilege(
                () -> executeAs(
                        postgres,
                        RUNTIME_USER,
                        RUNTIME_PASSWORD,
                        "CREATE TABLE vra.runtime_forbidden (id BIGINT)"
                )
        );

        assertInsufficientPrivilege(
                () -> executeAs(
                        postgres,
                        RUNTIME_USER,
                        RUNTIME_PASSWORD,
                        "ALTER TABLE vra.inventory_balance ADD COLUMN runtime_forbidden BIGINT"
                )
        );

        assertInsufficientPrivilege(
                () -> executeAs(
                        postgres,
                        RUNTIME_USER,
                        RUNTIME_PASSWORD,
                        "DROP TABLE vra.inventory_balance"
                )
        );

        assertInsufficientPrivilege(
                () -> executeAs(
                        postgres,
                        RUNTIME_USER,
                        RUNTIME_PASSWORD,
                        "CREATE SCHEMA runtime_forbidden"
                )
        );

        assertInsufficientPrivilege(
                () -> executeAs(
                        postgres,
                        RUNTIME_USER,
                        RUNTIME_PASSWORD,
                        "SELECT * FROM vra.flyway_schema_history"
                )
        );
    }

    private static void seedAvailableBalanceAsOwner(
            PostgreSQLContainer postgres,
            UUID skuId,
            UUID ownerId,
            UUID locationId
    ) throws SQLException {
        try (Connection connection = adminConnection(postgres)) {
            try (Statement role = connection.createStatement()) {
                role.execute("SET ROLE vra_owner");
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO vra.inventory_balance (
                        sku_id,
                        owner_id,
                        location_id,
                        stock_status,
                        on_hand,
                        reserved,
                        version
                    )
                    VALUES (?, ?, ?, 'AVAILABLE', 10, 0, 0)
                    """)) {

                insert.setObject(1, skuId);
                insert.setObject(2, ownerId);
                insert.setObject(3, locationId);

                assertEquals(1, insert.executeUpdate());
            }
        }
    }

    private static void corruptMigrationChecksum(PostgreSQLContainer postgres)
            throws SQLException {
        try (Connection connection = adminConnection(postgres)) {
            try (Statement role = connection.createStatement()) {
                role.execute("SET ROLE vra_owner");
            }

            try (Statement statement = connection.createStatement()) {
                assertEquals(
                        1,
                        statement.executeUpdate("""
                                UPDATE vra.flyway_schema_history
                                SET checksum = COALESCE(checksum, 0) + 1
                                WHERE version = '1'
                                """)
                );
            }
        }
    }

    private static void executeAs(
            PostgreSQLContainer postgres,
            String username,
            String password,
            String sql
    ) throws SQLException {
        try (Connection connection = roleConnection(postgres, username, password);
             Statement statement = connection.createStatement()) {

            statement.execute(sql);
        }
    }

    private static void assertInsufficientPrivilege(SqlAction action) {
        SQLException error = assertThrows(SQLException.class, action::run);
        assertEquals("42501", error.getSQLState());
    }

    private static Connection adminConnection(PostgreSQLContainer postgres)
            throws SQLException {
        return roleConnection(
                postgres,
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    private static Connection roleConnection(
            PostgreSQLContainer postgres,
            String username,
            String password
    ) throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                username,
                password
        );
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
