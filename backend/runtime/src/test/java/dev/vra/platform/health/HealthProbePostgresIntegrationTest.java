package dev.vra.platform.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

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
import static org.junit.jupiter.api.Assertions.fail;

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthProbePostgresIntegrationTest {

    private static final String DATABASE = "vra_health_test";
    private static final String ADMIN_USER = "postgres";
    private static final String ADMIN_PASSWORD = "health-admin-test-only";
    private static final String MIGRATOR_USER = "vra_migrator";
    private static final String MIGRATOR_PASSWORD =
            "health-migrator-test-only";
    private static final String RUNTIME_USER = "vra_runtime";
    private static final String RUNTIME_PASSWORD =
            "health-runtime-test-only";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.11")
                    .withDatabaseName(DATABASE)
                    .withUsername(ADMIN_USER)
                    .withPassword(ADMIN_PASSWORD);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

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
        registry.add(
                "vra.database.url",
                HealthProbePostgresIntegrationTest::runtimeJdbcUrl
        );
        registry.add(
                "vra.database.username",
                () -> RUNTIME_USER
        );
        registry.add(
                "vra.database.password",
                () -> RUNTIME_PASSWORD
        );
    }

    @LocalServerPort
    private int port;

    @Test
    void databaseOutageMakesReadinessDownButKeepsLivenessUp()
            throws Exception {
        HttpResponse<String> livenessBefore = get(
                "/actuator/health/liveness"
        );
        HttpResponse<String> readinessBefore = get(
                "/actuator/health/readiness"
        );

        assertHealth(livenessBefore, 200, "UP");
        assertHealth(readinessBefore, 200, "UP");

        POSTGRES.stop();

        HttpResponse<String> readinessAfter =
                awaitReadinessDown(Duration.ofSeconds(20));

        assertHealth(readinessAfter, 503, "DOWN");

        HttpResponse<String> livenessAfter = get(
                "/actuator/health/liveness"
        );

        assertHealth(livenessAfter, 200, "UP");
    }

    private HttpResponse<String> awaitReadinessDown(Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpResponse<String> lastResponse = null;

        while (System.nanoTime() < deadline) {
            lastResponse = get("/actuator/health/readiness");

            if (lastResponse.statusCode() == 503
                    && "DOWN".equals(
                            JsonPath.read(
                                    lastResponse.body(),
                                    "$.status"
                            )
                    )) {
                return lastResponse;
            }

            Thread.sleep(250);
        }

        if (lastResponse == null) {
            fail("Readiness probe produced no response");
        }

        fail(
                "Readiness did not become DOWN after database outage. "
                        + "Last HTTP status="
                        + lastResponse.statusCode()
                        + ", body="
                        + lastResponse.body()
        );

        throw new AssertionError("unreachable");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://127.0.0.1:" + port + path
                ))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static void assertHealth(
            HttpResponse<String> response,
            int expectedHttpStatus,
            String expectedHealthStatus
    ) {
        assertEquals(expectedHttpStatus, response.statusCode());
        assertEquals(
                expectedHealthStatus,
                JsonPath.read(response.body(), "$.status")
        );
    }

    private static String runtimeJdbcUrl() {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";

        return jdbcUrl
                + separator
                + "connectTimeout=1"
                + "&socketTimeout=1";
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
                    PASSWORD 'health-migrator-test-only'
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
                    PASSWORD 'health-runtime-test-only'
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
}
