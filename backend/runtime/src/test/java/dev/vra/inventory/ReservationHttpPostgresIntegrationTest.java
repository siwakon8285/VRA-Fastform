package dev.vra.inventory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationHttpPostgresIntegrationTest {

    private static final String DATABASE = "vra_http_test";
    private static final String ADMIN_USER = "postgres";
    private static final String ADMIN_PASSWORD = "http-admin-test-only";
    private static final String MIGRATOR_USER = "vra_migrator";
    private static final String MIGRATOR_PASSWORD = "http-migrator-test-only";
    private static final String RUNTIME_USER = "vra_runtime";
    private static final String RUNTIME_PASSWORD = "http-runtime-test-only";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.11")
                    .withDatabaseName(DATABASE)
                    .withUsername(ADMIN_USER)
                    .withPassword(ADMIN_PASSWORD);

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newHttpClient();

    static {
        POSTGRES.start();

        try {
            bootstrapRolesAndSchema();

            int migrated = new MigrationRunner().migrate(
                    POSTGRES.getJdbcUrl(),
                    MIGRATOR_USER,
                    MIGRATOR_PASSWORD
            );

            if (migrated != 1) {
                throw new IllegalStateException(
                        "Expected exactly one migration, got " + migrated
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

    @LocalServerPort
    private int port;

    @Test
    void realHttpRequestPersistsReservationInPostgres() throws Exception {
        InventoryIds ids = InventoryIds.random();
        UUID clientRequestId = UUID.randomUUID();

        seedBalance(ids, "AVAILABLE", 10, 0, 0);

        HttpResponse<String> response = postReservation(
                ids,
                "AVAILABLE",
                3,
                clientRequestId.toString()
        );

        assertEquals(201, response.statusCode());

        String responseRequestId = response.headers()
                .firstValue("X-Request-Id")
                .orElseThrow();

        assertNotEquals(clientRequestId.toString(), responseRequestId);
        UUID.fromString(responseRequestId);

        String bodyRequestId = JsonPath.read(
                response.body(),
                "$.request_id"
        );
        String reservationIdText = JsonPath.read(
                response.body(),
                "$.reservation_id"
        );
        Number inventoryVersion = JsonPath.read(
                response.body(),
                "$.inventory_version"
        );

        assertEquals(responseRequestId, bodyRequestId);
        assertEquals(1L, inventoryVersion.longValue());

        UUID reservationId = UUID.fromString(reservationIdText);

        assertBalance(ids, "AVAILABLE", 10, 3, 1);
        assertReservation(
                reservationId,
                ids,
                "AVAILABLE",
                3
        );
    }

    @Test
    void realHttpInsufficientStockReturnsStableErrorWithoutMutation()
            throws Exception {
        InventoryIds ids = InventoryIds.random();

        seedBalance(ids, "AVAILABLE", 2, 0, 0);

        HttpResponse<String> response = postReservation(
                ids,
                "AVAILABLE",
                3,
                null
        );

        assertEquals(409, response.statusCode());

        String requestId = response.headers()
                .firstValue("X-Request-Id")
                .orElseThrow();

        UUID.fromString(requestId);

        assertEquals(
                "INVENTORY_INSUFFICIENT_STOCK",
                JsonPath.read(response.body(), "$.code")
        );
        assertEquals(
                "Insufficient stock",
                JsonPath.read(response.body(), "$.message")
        );
        assertEquals(
                requestId,
                JsonPath.read(response.body(), "$.request_id")
        );

        assertBalance(ids, "AVAILABLE", 2, 0, 0);
        assertReservationCount(ids, 0);
    }

    @Test
    void realHttpUsesRuntimeLeastPrivilegeIdentity() throws Exception {
        InventoryIds ids = InventoryIds.random();

        seedBalance(ids, "AVAILABLE", 5, 0, 0);

        HttpResponse<String> response = postReservation(
                ids,
                "AVAILABLE",
                1,
                null
        );

        assertEquals(201, response.statusCode());

        assertEquals(
                RUNTIME_USER,
                currentDatabaseUserThroughRuntimeGrantEvidence()
        );
    }

    @Test
    void realHttpRejectsLegacyExpectedVersionWithoutMutation() throws Exception {
        InventoryIds ids = InventoryIds.random();
        seedBalance(ids, "AVAILABLE", 10, 2, 7);
        assertBalance(ids, "AVAILABLE", 10, 2, 7);
        assertReservationCount(ids, 0);

        String body = """
                {
                  "skuId": "%s",
                  "ownerId": "%s",
                  "locationId": "%s",
                  "stockStatus": "AVAILABLE",
                  "quantity": 3,
                  "expectedVersion": 7
                }
                """.formatted(ids.skuId(), ids.ownerId(), ids.locationId());
        HttpResponse<String> response = postJson(body, "client-controlled-value");

        assertEquals(400, response.statusCode(), response.body());
        assertEquals("REQUEST_INVALID", JsonPath.read(response.body(), "$.code"));
        assertEquals("Invalid request", JsonPath.read(response.body(), "$.message"));
        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        UUID.fromString(requestId);
        assertNotEquals("client-controlled-value", requestId);
        assertEquals(requestId, JsonPath.read(response.body(), "$.request_id"));
        assertBalance(ids, "AVAILABLE", 10, 2, 7);
        assertReservationCount(ids, 0);
    }

    private HttpResponse<String> postReservation(
            InventoryIds ids,
            String stockStatus,
            long quantity,
            String suppliedRequestId
    ) throws Exception {
        String body = """
                {
                  "skuId": "%s",
                  "ownerId": "%s",
                  "locationId": "%s",
                  "stockStatus": "%s",
                  "quantity": %d
                }
                """.formatted(
                ids.skuId(),
                ids.ownerId(),
                ids.locationId(),
                stockStatus,
                quantity
        );

        return postJson(body, suppliedRequestId);
    }

    private HttpResponse<String> postJson(String body, String suppliedRequestId)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://127.0.0.1:"
                                + port
                                + "/api/v1/inventory/reservations"
                ))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (suppliedRequestId != null) {
            request.header("X-Request-Id", suppliedRequestId);
        }

        return HTTP_CLIENT.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
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
                    PASSWORD 'http-migrator-test-only'
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
                    PASSWORD 'http-runtime-test-only'
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

    private static void seedBalance(
            InventoryIds ids,
            String stockStatus,
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

                statement.setObject(1, ids.skuId());
                statement.setObject(2, ids.ownerId());
                statement.setObject(3, ids.locationId());
                statement.setString(4, stockStatus);
                statement.setLong(5, onHand);
                statement.setLong(6, reserved);
                statement.setLong(7, version);

                assertEquals(1, statement.executeUpdate());
            }
        });
    }

    private static void assertBalance(
            InventoryIds ids,
            String stockStatus,
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

            statement.setObject(1, ids.skuId());
            statement.setObject(2, ids.ownerId());
            statement.setObject(3, ids.locationId());
            statement.setString(4, stockStatus);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(
                        expectedOnHand,
                        result.getLong("on_hand")
                );
                assertEquals(
                        expectedReserved,
                        result.getLong("reserved")
                );
                assertEquals(
                        expectedVersion,
                        result.getLong("version")
                );
            }
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static void assertReservation(
            UUID reservationId,
            InventoryIds ids,
            String stockStatus,
            long expectedQuantity
    ) {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         sku_id,
                         owner_id,
                         location_id,
                         stock_status,
                         quantity
                     FROM vra.inventory_reservation
                     WHERE reservation_id = ?
                     """)) {

            statement.setObject(1, reservationId);

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(ids.skuId(), result.getObject("sku_id", UUID.class));
                assertEquals(ids.ownerId(), result.getObject("owner_id", UUID.class));
                assertEquals(
                        ids.locationId(),
                        result.getObject("location_id", UUID.class)
                );
                assertEquals(stockStatus, result.getString("stock_status"));
                assertEquals(
                        expectedQuantity,
                        result.getLong("quantity")
                );
            }
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static void assertReservationCount(
            InventoryIds ids,
            long expectedCount
    ) {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM vra.inventory_reservation
                     WHERE sku_id = ?
                       AND owner_id = ?
                       AND location_id = ?
                     """)) {

            statement.setObject(1, ids.skuId());
            statement.setObject(2, ids.ownerId());
            statement.setObject(3, ids.locationId());

            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(expectedCount, result.getLong(1));
            }
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private static String currentDatabaseUserThroughRuntimeGrantEvidence() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                RUNTIME_USER,
                RUNTIME_PASSWORD
        );
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT current_user"
             )) {

            assertTrue(result.next());
            String currentUser = result.getString(1);
            assertNotNull(currentUser);

            return currentUser;
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
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

    private record InventoryIds(
            UUID skuId,
            UUID ownerId,
            UUID locationId
    ) {
        static InventoryIds random() {
            return new InventoryIds(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
