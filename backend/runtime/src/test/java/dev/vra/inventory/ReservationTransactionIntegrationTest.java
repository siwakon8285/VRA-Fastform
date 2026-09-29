package dev.vra.inventory;

import dev.vra.async.AsyncRoleBootstrap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import dev.vra.inventory.application.ReservationApplicationService;
import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationFailureException;
import dev.vra.inventory.application.ReserveInventoryCommand;
import dev.vra.inventory.domain.InventoryKey;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReservationTransactionIntegrationTest {

    private static final String DATABASE = "vra_runtime_test";
    private static final String ADMIN_USER = "postgres";
    private static final String ADMIN_PASSWORD = "runtime-admin-test-only";
    private static final String MIGRATOR_USER = "vra_migrator";
    private static final String MIGRATOR_PASSWORD = "runtime-migrator-test-only";
    private static final String RUNTIME_USER = "vra_runtime";
    private static final String RUNTIME_PASSWORD = "runtime-app-test-only";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.11")
                    .withDatabaseName(DATABASE)
                    .withUsername(ADMIN_USER)
                    .withPassword(ADMIN_PASSWORD);

    static {
        POSTGRES.start();

        try {
            bootstrapRolesAndSchema();
            AsyncRoleBootstrap.run(POSTGRES);
            int migrated = new MigrationRunner().migrate(
                    POSTGRES.getJdbcUrl(),
                    MIGRATOR_USER,
                    MIGRATOR_PASSWORD
            );

            if (migrated != 3) {
                throw new IllegalStateException(
                        "Expected exactly three migrations, got " + migrated
                );
            }
        } catch (Exception error) {
            POSTGRES.stop();
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void runtimeDatabaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("vra.database.url", POSTGRES::getJdbcUrl);
        registry.add("vra.database.username", () -> RUNTIME_USER);
        registry.add("vra.database.password", () -> RUNTIME_PASSWORD);
    }

    @Autowired
    private ReservationApplicationService reservationApplicationService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void runtimeConnectsUsingLeastPrivilegeIdentity() {
        assertEquals(
                RUNTIME_USER,
                jdbcClient.sql("SELECT current_user")
                        .query(String.class)
                        .single()
        );
    }

    @Test
    void reservesInventoryAndPersistsReservationInOneTransaction() {
        InventoryKey key = newKey(StockStatus.AVAILABLE);
        seedBalance(key, 10, 0, 0);

        var result = reservationApplicationService.reserve(
                new ReserveInventoryCommand(
                        key,
                        3
                )
        );

        assertEquals(1, result.inventoryVersion());

        assertBalance(key, 10, 3, 1);
        assertReservation(result.reservationId(), 3);
    }

    @Test
    void insufficientStockDoesNotCreateReservationOrChangeBalance() {
        InventoryKey key = newKey(StockStatus.AVAILABLE);
        seedBalance(key, 2, 0, 0);

        var error = assertThrows(
                ReservationFailureException.class,
                () -> reservationApplicationService.reserve(
                        new ReserveInventoryCommand(
                                key,
                                3
                        )
                )
        );

        assertEquals(
                ReservationFailureCode.INSUFFICIENT_STOCK,
                error.code()
        );

        assertBalance(key, 2, 0, 0);
        assertReservationCount(key, 0);
    }

    @Test
    void quarantinedInventoryIsNotReservable() {
        InventoryKey key = newKey(StockStatus.QUARANTINED);
        seedBalance(key, 10, 0, 0);

        var error = assertThrows(
                ReservationFailureException.class,
                () -> reservationApplicationService.reserve(
                        new ReserveInventoryCommand(
                                key,
                                1
                        )
                )
        );

        assertEquals(
                ReservationFailureCode.INVENTORY_NOT_RESERVABLE,
                error.code()
        );

        assertBalance(key, 10, 0, 0);
        assertReservationCount(key, 0);
    }

    @Test
    void inventoryKeyKeepsOwnerAndLocationIsolated() {
        UUID skuId = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID locationA = UUID.randomUUID();
        UUID locationB = UUID.randomUUID();

        InventoryKey target = new InventoryKey(
                skuId,
                ownerA,
                locationA,
                StockStatus.AVAILABLE
        );

        InventoryKey otherOwner = new InventoryKey(
                skuId,
                ownerB,
                locationA,
                StockStatus.AVAILABLE
        );

        InventoryKey otherLocation = new InventoryKey(
                skuId,
                ownerA,
                locationB,
                StockStatus.AVAILABLE
        );

        seedBalance(target, 10, 0, 0);
        seedBalance(otherOwner, 20, 0, 0);
        seedBalance(otherLocation, 30, 0, 0);

        reservationApplicationService.reserve(
                new ReserveInventoryCommand(
                        target,
                        4
                )
        );

        assertBalance(target, 10, 4, 1);
        assertBalance(otherOwner, 20, 0, 0);
        assertBalance(otherLocation, 30, 0, 0);
    }

    @Test
    void staleOptimisticCompareAndSwapDoesNotMutateState() {
        InventoryKey key = newKey(StockStatus.AVAILABLE);
        seedBalance(key, 10, 2, 10);

        assertEquals(1, conditionalVersionUpdate(key, 10));
        assertEquals(0, conditionalVersionUpdate(key, 10));

        assertBalance(key, 10, 2, 11);
        assertReservationCount(key, 0);
    }

    @Test
    void databaseRejectsNegativeOnHand() {
        assertBalanceCheckViolation(-1, 0, 0, "AVAILABLE");
    }

    @Test
    void databaseRejectsNegativeReserved() {
        assertBalanceCheckViolation(10, -1, 0, "AVAILABLE");
    }

    @Test
    void databaseRejectsReservedGreaterThanOnHand() {
        assertBalanceCheckViolation(10, 11, 0, "AVAILABLE");
    }

    @Test
    void databaseRejectsInvalidStockStatus() {
        assertBalanceCheckViolation(10, 0, 0, "DAMAGED");
    }

    @Test
    void databaseRejectsNonPositiveReservationQuantity() {
        InventoryKey key = newKey(StockStatus.AVAILABLE);
        seedBalance(key, 10, 0, 0);

        SQLException error = assertThrows(
                SQLException.class,
                () -> insertReservationAsOwner(
                        UUID.randomUUID(),
                        key,
                        0
                )
        );

        assertEquals("23514", error.getSQLState());
    }

    @Test
    void reservationInsertFailureRollsBackPriorJdbcInventoryUpdate() {
        InventoryKey key = newKey(StockStatus.AVAILABLE);
        seedBalance(key, 10, 0, 0);
        addReservationInsertFailureConstraint(key);
        try {
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> reservationApplicationService.reserve(
                            new ReserveInventoryCommand(key, 2)
                    )
            );

            assertBalance(key, 10, 0, 0);
            assertReservationCount(key, 0);
        } finally {
            dropReservationInsertFailureConstraint();
        }
    }

    private static int conditionalVersionUpdate(
            InventoryKey key,
            long expectedVersion
    ) {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE vra.inventory_balance
                     SET version = version + 1
                     WHERE sku_id = ?
                       AND owner_id = ?
                       AND location_id = ?
                       AND stock_status = ?
                       AND version = ?
                     """)) {
            statement.setObject(1, key.skuId());
            statement.setObject(2, key.ownerId());
            statement.setObject(3, key.locationId());
            statement.setString(4, key.stockStatus().name());
            statement.setLong(5, expectedVersion);
            return statement.executeUpdate();
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static void addReservationInsertFailureConstraint(InventoryKey key) {
        asOwner(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE vra.inventory_reservation
                        ADD CONSTRAINT reservation_insert_failure_test
                        CHECK (sku_id <> '%s'::uuid)
                        """.formatted(key.skuId()));
            }
        });
    }

    private static void dropReservationInsertFailureConstraint() {
        asOwner(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE vra.inventory_reservation
                        DROP CONSTRAINT reservation_insert_failure_test
                        """);
            }
        });
    }

    private static void bootstrapRolesAndSchema() throws SQLException {
        try (Connection connection = adminConnection();
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
                    PASSWORD 'runtime-migrator-test-only'
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
                    PASSWORD 'runtime-app-test-only'
                    """);

            statement.execute("""
                    GRANT vra_owner TO vra_migrator
                    WITH ADMIN FALSE, INHERIT FALSE, SET TRUE
                    """);

            statement.execute(
                    "REVOKE CREATE ON DATABASE "
                            + quoteIdentifier(DATABASE)
                            + " FROM PUBLIC"
            );

            statement.execute(
                    "GRANT CONNECT ON DATABASE "
                            + quoteIdentifier(DATABASE)
                            + " TO vra_migrator, vra_runtime"
            );

            statement.execute(
                    "CREATE SCHEMA vra AUTHORIZATION vra_owner"
            );
            statement.execute(
                    "GRANT USAGE ON SCHEMA vra TO vra_runtime"
            );
        }
    }

    private static void assertBalanceCheckViolation(
            long onHand,
            long reserved,
            long version,
            String stockStatus
    ) {
        SQLException error = assertThrows(
                SQLException.class,
                () -> insertBalanceAsOwner(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        stockStatus,
                        onHand,
                        reserved,
                        version
                )
        );

        assertEquals("23514", error.getSQLState());
    }

    private static void insertBalanceAsOwner(
            UUID skuId,
            UUID ownerId,
            UUID locationId,
            String stockStatus,
            long onHand,
            long reserved,
            long version
    ) throws SQLException {
        try (Connection connection = adminConnection()) {
            try (Statement role = connection.createStatement()) {
                role.execute("SET ROLE vra_owner");
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vra.inventory_balance (
                        sku_id,
                        owner_id,
                        location_id,
                        stock_status,
                        on_hand,
                        reserved,
                        version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {

                statement.setObject(1, skuId);
                statement.setObject(2, ownerId);
                statement.setObject(3, locationId);
                statement.setString(4, stockStatus);
                statement.setLong(5, onHand);
                statement.setLong(6, reserved);
                statement.setLong(7, version);

                statement.executeUpdate();
            }
        }
    }

    private static void insertReservationAsOwner(
            UUID reservationId,
            InventoryKey key,
            long quantity
    ) throws SQLException {
        try (Connection connection = adminConnection()) {
            try (Statement role = connection.createStatement()) {
                role.execute("SET ROLE vra_owner");
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vra.inventory_reservation (
                        reservation_id,
                        sku_id,
                        owner_id,
                        location_id,
                        stock_status,
                        quantity,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """)) {

                statement.setObject(1, reservationId);
                statement.setObject(2, key.skuId());
                statement.setObject(3, key.ownerId());
                statement.setObject(4, key.locationId());
                statement.setString(5, key.stockStatus().name());
                statement.setLong(6, quantity);

                statement.executeUpdate();
            }
        }
    }

    private static void seedBalance(
            InventoryKey key,
            long onHand,
            long reserved,
            long version
    ) {
        asOwner(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vra.inventory_balance (
                        sku_id,
                        owner_id,
                        location_id,
                        stock_status,
                        on_hand,
                        reserved,
                        version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {

                statement.setObject(1, key.skuId());
                statement.setObject(2, key.ownerId());
                statement.setObject(3, key.locationId());
                statement.setString(4, key.stockStatus().name());
                statement.setLong(5, onHand);
                statement.setLong(6, reserved);
                statement.setLong(7, version);

                assertEquals(1, statement.executeUpdate());
            }
        });
    }

    private static void assertBalance(
            InventoryKey key,
            long expectedOnHand,
            long expectedReserved,
            long expectedVersion
    ) {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT on_hand, reserved, version
                     FROM vra.inventory_balance
                     WHERE sku_id = ?
                       AND owner_id = ?
                       AND location_id = ?
                       AND stock_status = ?
                     """)) {

            statement.setObject(1, key.skuId());
            statement.setObject(2, key.ownerId());
            statement.setObject(3, key.locationId());
            statement.setString(4, key.stockStatus().name());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(expectedOnHand, result.getLong("on_hand"));
                assertEquals(expectedReserved, result.getLong("reserved"));
                assertEquals(expectedVersion, result.getLong("version"));
            }
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static void assertReservation(
            UUID reservationId,
            long expectedQuantity
    ) {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT quantity
                     FROM vra.inventory_reservation
                     WHERE reservation_id = ?
                     """)) {

            statement.setObject(1, reservationId);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(
                        expectedQuantity,
                        result.getLong("quantity")
                );
            }
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static void assertReservationCount(InventoryKey key, long expectedCount) {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM vra.inventory_reservation
                     WHERE sku_id = ?
                       AND owner_id = ?
                       AND location_id = ?
                       AND stock_status = ?
                     """)) {

            statement.setObject(1, key.skuId());
            statement.setObject(2, key.ownerId());
            statement.setObject(3, key.locationId());
            statement.setString(4, key.stockStatus().name());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(expectedCount, result.getLong(1));
            }
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static InventoryKey newKey(StockStatus status) {
        return new InventoryKey(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status
        );
    }

    private static void asOwner(SqlConsumer action) {
        try (Connection connection = adminConnection()) {
            try (Statement role = connection.createStatement()) {
                role.execute("SET ROLE vra_owner");
            }

            action.accept(connection);
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
