package dev.vra.migration;

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
                    1,
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

            verifyRuntimeDmlAndDdlBoundary(postgres);

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
