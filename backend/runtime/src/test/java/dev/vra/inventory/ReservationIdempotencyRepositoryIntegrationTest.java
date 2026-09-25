package dev.vra.inventory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationRequestFingerprint.Fingerprint;
import dev.vra.inventory.application.port.ReservationIdempotencyRepository;
import dev.vra.inventory.application.port.ReservationIdempotencyRepository.ClaimResult;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReservationIdempotencyRepositoryIntegrationTest {
    private static final String DATABASE = "vra_idempotency_test";
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
            int migrated = new MigrationRunner().migrate(
                    POSTGRES.getJdbcUrl(),
                    MIGRATOR_USER,
                    MIGRATOR_PASSWORD
            );

            if (migrated != 2) {
                throw new IllegalStateException(
                        "Expected exactly two migrations, got " + migrated
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


    private static final String ACTOR = "repository-test";
    private static final Fingerprint A = new Fingerprint((short) 1, "a".repeat(64));
    private static final Fingerprint B = new Fingerprint((short) 1, "b".repeat(64));
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant COMPLETED = CREATED.plusSeconds(1);

    @Autowired
    private ReservationIdempotencyRepository repository;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void successLifecycleReplaysAndCannotBeRewritten() throws Exception {
        String key = UUID.randomUUID().toString();
        UUID sku = seedBalance();
        UUID reservation = UUID.randomUUID();
        transaction().executeWithoutResult(status -> {
            assertEquals("read committed",
                    jdbcClient.sql("SHOW transaction_isolation").query(String.class).single());
            assertEquals(RUNTIME_USER,
                    jdbcClient.sql("SELECT current_user").query(String.class).single());
            assertInstanceOf(ClaimResult.Claimed.class,
                    repository.claimOrResolve(ACTOR, key, A, CREATED));
            insertReservation(sku, reservation);
            repository.completeSucceeded(ACTOR, key, A, reservation, 11, COMPLETED);
        });
        Snapshot original = snapshot(key);
        assertEquals("SUCCEEDED", original.status());
        assertEquals(reservation, original.reservation());
        assertEquals(11L, original.version());
        assertNull(original.rejection());
        assertEquals(CREATED, original.created());
        assertEquals(COMPLETED, original.completed());
        assertEquals(new ClaimResult.ReplayedSuccess(reservation, 11), resolve(key, A));
        assertInstanceOf(ClaimResult.FingerprintConflict.class, resolve(key, B));
        assertEquals(original, snapshot(key));

        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(status ->
                repository.completeSucceeded(ACTOR, key, A, UUID.randomUUID(), 99,
                        COMPLETED.plusSeconds(1))));
        assertEquals(original, snapshot(key));
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(status ->
                repository.completeRejected(ACTOR, key, A,
                        ReservationFailureCode.INSUFFICIENT_STOCK, COMPLETED.plusSeconds(1))));
        assertEquals(original, snapshot(key));
    }

    @Test
    void rejectionLifecycleReplaysAndCannotBeRewritten() {
        String key = UUID.randomUUID().toString();
        transaction().executeWithoutResult(status -> {
            assertEquals("read committed",
                    jdbcClient.sql("SHOW transaction_isolation").query(String.class).single());
            assertInstanceOf(ClaimResult.Claimed.class,
                    repository.claimOrResolve(ACTOR, key, A, CREATED));
            repository.completeRejected(ACTOR, key, A,
                    ReservationFailureCode.INSUFFICIENT_STOCK, COMPLETED);
        });
        Snapshot original = snapshot(key);
        assertEquals("REJECTED", original.status());
        assertNull(original.reservation());
        assertNull(original.version());
        assertEquals("INSUFFICIENT_STOCK", original.rejection());
        assertEquals(CREATED, original.created());
        assertEquals(COMPLETED, original.completed());
        assertEquals(new ClaimResult.ReplayedRejection(ReservationFailureCode.INSUFFICIENT_STOCK),
                resolve(key, A));
        assertInstanceOf(ClaimResult.FingerprintConflict.class, resolve(key, B));
        assertEquals(original, snapshot(key));

        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(status ->
                repository.completeRejected(ACTOR, key, A,
                        ReservationFailureCode.INVENTORY_NOT_FOUND, COMPLETED.plusSeconds(1))));
        assertEquals(original, snapshot(key));
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(status ->
                repository.completeSucceeded(ACTOR, key, A, UUID.randomUUID(), 99,
                        COMPLETED.plusSeconds(1))));
        assertEquals(original, snapshot(key));
    }

    @Test
    void wrongFingerprintCannotCompleteAndClaimIsRolledBack() {
        String key = UUID.randomUUID().toString();
        transaction().executeWithoutResult(status -> {
            assertInstanceOf(ClaimResult.Claimed.class,
                    repository.claimOrResolve(ACTOR, key, A, CREATED));
            Snapshot original = snapshot(key);
            for (Fingerprint wrong : new Fingerprint[]{B, new Fingerprint((short) 2, A.value())}) {
                assertThrows(IllegalStateException.class, () ->
                        repository.completeRejected(ACTOR, key, wrong,
                                ReservationFailureCode.INSUFFICIENT_STOCK, COMPLETED));
                assertThrows(IllegalStateException.class, () ->
                        repository.completeSucceeded(ACTOR, key, wrong, UUID.randomUUID(), 11, COMPLETED));
                assertEquals(original, snapshot(key));
            }
            status.setRollbackOnly();
        });
        assertEquals(0L, jdbcClient.sql("""
                SELECT COUNT(*) FROM vra.inventory_reservation_idempotency
                WHERE actor_scope = :actor AND idempotency_key = :key
                """).param("actor", ACTOR).param("key", key).query(Long.class).single());
    }

    @Test
    void committedIncompleteFixtureFailsBeforeFingerprintConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        try {
            // Deliberately invalid durable lifecycle, only for this defensive fixture.
            transaction().executeWithoutResult(status ->
                    assertInstanceOf(ClaimResult.Claimed.class,
                            repository.claimOrResolve(ACTOR, key, A, CREATED)));
            Snapshot original = snapshot(key);
            assertNull(original.status());
            assertThrows(IllegalStateException.class, () -> resolve(key, A));
            assertThrows(IllegalStateException.class, () -> resolve(key, B));
            assertEquals(original, snapshot(key));
        } finally {
            deleteFixtureAsOwner(key);
        }
    }

    @Test
    void unknownPersistedRejectionFailsAsInvariant() {
        String key = UUID.randomUUID().toString();
        transaction().executeWithoutResult(status -> {
            repository.claimOrResolve(ACTOR, key, A, CREATED);
            repository.completeRejected(ACTOR, key, A,
                    ReservationFailureCode.INSUFFICIENT_STOCK, COMPLETED);
            // Schema permits arbitrary rejection text; adapter must validate its enum meaning.
            jdbcClient.sql("""
                    UPDATE vra.inventory_reservation_idempotency SET rejection_code = 'UNKNOWN_CODE'
                    WHERE actor_scope = :actor AND idempotency_key = :key
                    """).param("actor", ACTOR).param("key", key).update();
        });
        assertThrows(IllegalStateException.class, () -> resolve(key, A));
    }

    @Test
    void missingClaimCannotBeCompleted() {
        String key = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(status ->
                repository.completeSucceeded(ACTOR, key, A, UUID.randomUUID(), 1, COMPLETED)));
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(status ->
                repository.completeRejected(ACTOR, key, A,
                        ReservationFailureCode.INSUFFICIENT_STOCK, COMPLETED)));
    }

    @AfterEach
    void committedRowsHaveNoNullOutcome() {
        assertEquals(0L, jdbcClient.sql("""
                SELECT COUNT(*) FROM vra.inventory_reservation_idempotency
                WHERE outcome_status IS NULL
                """).query(Long.class).single());
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }

    private ClaimResult resolve(String key, Fingerprint fingerprint) {
        return transaction().execute(status ->
                repository.claimOrResolve(ACTOR, key, fingerprint, CREATED.plusSeconds(10)));
    }

    private Snapshot snapshot(String key) {
        return jdbcClient.sql("""
                SELECT fingerprint_version, request_fingerprint, outcome_status,
                       reservation_id, inventory_version, rejection_code, created_at, completed_at
                FROM vra.inventory_reservation_idempotency
                WHERE actor_scope = :actor AND idempotency_key = :key
                """).param("actor", ACTOR).param("key", key)
                .query((rs, n) -> new Snapshot(
                        rs.getShort("fingerprint_version"), rs.getString("request_fingerprint"),
                        rs.getString("outcome_status"), rs.getObject("reservation_id", UUID.class),
                        rs.getObject("inventory_version", Long.class), rs.getString("rejection_code"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("completed_at") == null ? null
                                : rs.getTimestamp("completed_at").toInstant())).single();
    }

    private record Snapshot(short fingerprintVersion, String fingerprint, String status,
                            UUID reservation, Long version, String rejection,
                            Instant created, Instant completed) {}

    private static UUID seedBalance() throws SQLException {
        UUID sku = UUID.randomUUID();
        try (Connection connection = adminConnection();
             Statement role = connection.createStatement()) {
            role.execute("SET ROLE vra_owner");
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO vra.inventory_balance
                        (sku_id, owner_id, location_id, stock_status, on_hand, reserved, version)
                    VALUES (?, ?, ?, 'AVAILABLE', 10, 1, 11)
                    """)) {
                insert.setObject(1, sku);
                insert.setObject(2, sku);
                insert.setObject(3, sku);
                assertEquals(1, insert.executeUpdate());
            }
        }
        return sku;
    }

    private void insertReservation(UUID sku, UUID reservation) {
        assertEquals(1, jdbcClient.sql("""
                INSERT INTO vra.inventory_reservation
                    (reservation_id, sku_id, owner_id, location_id, stock_status, quantity, created_at)
                VALUES (:reservation, :sku, :sku, :sku, 'AVAILABLE', 1, :created)
                """).param("reservation", reservation).param("sku", sku)
                .param("created", CREATED.atOffset(java.time.ZoneOffset.UTC)).update());
    }

    private static void deleteFixtureAsOwner(String key) throws SQLException {
        try (Connection connection = adminConnection();
             Statement role = connection.createStatement()) {
            role.execute("SET ROLE vra_owner");
            try (PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM vra.inventory_reservation_idempotency
                    WHERE actor_scope = ? AND idempotency_key = ?
                    """)) {
                delete.setString(1, ACTOR);
                delete.setString(2, key);
                delete.executeUpdate();
            }
        }
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
