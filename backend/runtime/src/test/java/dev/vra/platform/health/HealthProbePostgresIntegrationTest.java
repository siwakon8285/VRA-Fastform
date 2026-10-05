package dev.vra.platform.health;

import dev.vra.async.AsyncRoleBootstrap;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jayway.jsonpath.JsonPath;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(HealthProbePostgresIntegrationTest.BoundedHealthDataSource.class)
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
            new dev.vra.poc04.external.RunOwnedPostgreSQLContainer("postgres:17.11")
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
            AsyncRoleBootstrap.run(POSTGRES);

            int migrated = new MigrationRunner().migrate(
                    POSTGRES.getJdbcUrl(),
                    MIGRATOR_USER,
                    MIGRATOR_PASSWORD
            );

            if (migrated != 4) {
                throw new IllegalStateException(
                        "Expected exactly four migrations, got " + migrated
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

    @Autowired
    private DataSource dataSource;

    @TestConfiguration(proxyBeanMethods = false)
    static class BoundedHealthDataSource {
        @Bean
        static BeanPostProcessor healthDataSourceTimeouts() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String name) {
                    if ("dataSource".equals(name)) {
                        HikariDataSource pool = assertInstanceOf(HikariDataSource.class, bean);
                        assertNull(pool.getHikariPoolMXBean(), "TEST timeouts must precede pool initialization");
                        // Measured default acquisition was 30s, beyond the unchanged 5s HTTP deadline.
                        // Configure only this TEST bean before Hikari caches its acquisition timeout.
                        pool.setConnectionTimeout(2_000);
                        pool.setValidationTimeout(1_000);
                        printTiming(Map.of("event", "test_only_datasource_bound_before_initialization", "pool", poolState(pool)));
                    }
                    return bean;
                }
            };
        }
    }

    @Test
    void databaseOutageMakesReadinessDownButKeepsLivenessUp()
            throws Exception {
        HikariDataSource pool = assertInstanceOf(HikariDataSource.class, dataSource);
        assertEquals(2_000L, pool.getConnectionTimeout());
        assertEquals(1_000L, pool.getValidationTimeout());
        printTiming(Map.of("event", "actual_datasource_initialized_with_test_bound", "pool", poolState(pool)));
        var sampler = Executors.newSingleThreadScheduledExecutor();
        var livenessStarted = new AtomicBoolean();
        var concurrentLiveness = new CompletableFuture<HttpResponse<String>>();
        Runnable probeLiveness = () -> {
            if (!livenessStarted.compareAndSet(false, true)) return;
            try {
                concurrentLiveness.complete(get("liveness_while_degraded", "/actuator/health/liveness"));
            } catch (Exception failure) {
                concurrentLiveness.completeExceptionally(failure);
            }
        };
        try {
            try (Connection connection = dataSource.getConnection();
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT current_user, current_database(), current_setting('server_version_num')")) {
                result.next();
                assertEquals(RUNTIME_USER, result.getString(1));
                assertEquals(DATABASE, result.getString(2));
                assertEquals("170011", result.getString(3));
                printTiming(Map.of("event", "actual_runtime_database", "role", result.getString(1),
                        "database", result.getString(2), "server_version_num", result.getString(3)));
            }
            assertHealth(get("liveness_before", "/actuator/health/liveness"), 200, "UP");
            assertHealth(get("readiness_before", "/actuator/health/readiness"), 200, "UP");
            printTiming(Map.of("event", "database_stop_start", "at", Instant.now().toString(),
                    "container_id", POSTGRES.getContainerId(), "pool", poolState(pool)));
            POSTGRES.stop();
            printTiming(Map.of("event", "database_stop_complete", "at", Instant.now().toString(), "pool", poolState(pool)));
            // Sample an actual acquisition wait; no sleeps determine health correctness.
            sampler.scheduleAtFixedRate(() -> {
                if (pool.getHikariPoolMXBean().getThreadsAwaitingConnection() > 0 && !livenessStarted.get()) {
                    printTiming(Map.of("event", "readiness_acquisition_wait", "pool", poolState(pool)));
                    probeLiveness.run();
                }
            }, 0, 10, TimeUnit.MILLISECONDS);
            assertHealth(awaitReadinessDown(Duration.ofSeconds(20)), 503, "DOWN");
            // A fast query failure can return DOWN without an observable pool wait.
            probeLiveness.run();
            assertHealth(concurrentLiveness.get(5, TimeUnit.SECONDS), 200, "UP");
            assertHealth(get("liveness_after", "/actuator/health/liveness"), 200, "UP");
        } finally {
            sampler.shutdownNow();
            sampler.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private HttpResponse<String> awaitReadinessDown(Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpResponse<String> lastResponse = null;

        while (System.nanoTime() < deadline) {
            lastResponse = get("readiness_after_outage", "/actuator/health/readiness");

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

    private HttpResponse<String> get(String event, String path) throws Exception {
        long started = System.nanoTime();
        var timing = new LinkedHashMap<String, Object>();
        timing.put("event", event);
        timing.put("path", path);
        timing.put("started_at", Instant.now().toString());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://127.0.0.1:" + port + path
                ))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            timing.put("http_status", response.statusCode());
            timing.put("health_status", JsonPath.read(response.body(), "$.status"));
            return response;
        } catch (Exception failure) {
            timing.put("exception_class", failure.getClass().getName());
            throw failure;
        } finally {
            timing.put("ended_at", Instant.now().toString());
            timing.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000.0);
            printTiming(timing);
        }
    }

    private static Map<String, Object> poolState(HikariDataSource pool) {
        var state = new LinkedHashMap<String, Object>();
        state.put("class", pool.getClass().getName());
        state.put("connection_timeout_ms", pool.getConnectionTimeout());
        state.put("validation_timeout_ms", pool.getValidationTimeout());
        state.put("maximum_pool_size", pool.getMaximumPoolSize());
        state.put("minimum_idle", pool.getMinimumIdle());
        var counters = pool.getHikariPoolMXBean();
        if (counters != null) {
            state.put("active", counters.getActiveConnections());
            state.put("idle", counters.getIdleConnections());
            state.put("total", counters.getTotalConnections());
            state.put("awaiting_connection", counters.getThreadsAwaitingConnection());
        }
        return state;
    }

    private static void printTiming(Map<String, Object> timing) {
        System.out.println("POC04_HEALTH_TIMING " + JsonMapper.builder().build().writeValueAsString(timing));
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
