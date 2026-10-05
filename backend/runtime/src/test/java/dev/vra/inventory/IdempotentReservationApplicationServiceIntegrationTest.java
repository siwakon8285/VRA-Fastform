package dev.vra.inventory;

import dev.vra.async.AsyncRoleBootstrap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dev.vra.inventory.adapter.out.persistence.JdbcReservationIdempotencyRepository;
import dev.vra.inventory.adapter.out.persistence.JpaReservationRepository;
import dev.vra.inventory.application.IdempotentReservationApplicationService;
import dev.vra.inventory.application.IdempotentReserveInventoryCommand;
import dev.vra.inventory.application.IdempotentReservationResult;
import dev.vra.inventory.application.IdempotentReservationResult.IdempotencyFailureCode;
import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationFailureException;
import dev.vra.inventory.application.ReservationRequestFingerprint;
import dev.vra.inventory.application.port.ReservationIdempotencyRepository;
import dev.vra.inventory.application.port.ReservationRepository;
import dev.vra.inventory.domain.InventoryKey;
import dev.vra.inventory.domain.InventoryReservation;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdempotentReservationApplicationServiceIntegrationTest {
    private static final String DATABASE = "vra_idempotent_application_test";
    private static final String ADMIN_USER = "postgres";
    private static final String ADMIN_PASSWORD = "runtime-admin-test-only";
    private static final String MIGRATOR_USER = "vra_migrator";
    private static final String MIGRATOR_PASSWORD = "runtime-migrator-test-only";
    private static final String RUNTIME_USER = "vra_runtime";
    private static final String RUNTIME_PASSWORD = "runtime-app-test-only";

    private static final PostgreSQLContainer POSTGRES =
            new dev.vra.poc04.external.RunOwnedPostgreSQLContainer("postgres:17.11")
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
        registry.add("vra.database.url", POSTGRES::getJdbcUrl);
        registry.add("vra.database.username", () -> RUNTIME_USER);
        registry.add("vra.database.password", () -> RUNTIME_PASSWORD);
    }



    private static final String ACTOR = "test-buyer-0001";

    @Autowired
    private IdempotentReservationApplicationService service;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private ReservationRequestFingerprint fingerprint;
    @Autowired
    private ObservingIdempotencyRepository observer;
    @Autowired
    private FaultInjectingReservationRepository failureInjector;

    @Test
    void firstSuccessPersistsOneBusinessEffectAndTerminalOutcome() {
        InventoryKey key = seedBalance(StockStatus.AVAILABLE, 10);
        IdempotentReserveInventoryCommand command = command(key, 3);
        var success = assertInstanceOf(
                IdempotentReservationResult.Succeeded.class, service.reserve(command)
        );

        assertEquals(1L, success.inventoryVersion());
        assertEquals(new Balance(10, 3, 1), balance(key));
        assertEquals(1, reservationIds(key).size());
        assertEquals(success.reservationId(), reservationIds(key).getFirst());
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
        assertSucceededRow(command, success);
        assertEquals(new Observation("read committed", RUNTIME_USER), observer.observation());
    }

    @Test
    void samePayloadReplaysAndDifferentPayloadConflictsWithoutMutation() {
        InventoryKey key = seedBalance(StockStatus.AVAILABLE, 10);
        IdempotentReserveInventoryCommand command = command(key, 3);
        var first = assertInstanceOf(
                IdempotentReservationResult.Succeeded.class, service.reserve(command)
        );
        IdempotencyRow original = idempotencyRow(command);

        assertEquals(first, service.reserve(command));
        assertEquals(new Balance(10, 3, 1), balance(key));
        assertEquals(java.util.List.of(first.reservationId()), reservationIds(key));
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
        assertEquals(original, idempotencyRow(command));

        var changedQuantity = new IdempotentReserveInventoryCommand(
                command.actorScope(), command.idempotencyKey(), key, 2
        );
        assertEquals(
                new IdempotentReservationResult.Conflict(
                        IdempotencyFailureCode.IDEMPOTENCY_KEY_REUSED
                ),
                service.reserve(changedQuantity)
        );
        assertEquals(new Balance(10, 3, 1), balance(key));
        assertEquals(java.util.List.of(first.reservationId()), reservationIds(key));
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
        assertEquals(original, idempotencyRow(command));
    }

    @Test
    void insufficientStockRejectionReplaysAfterStockChangesAndNewKeyExecutes() {
        InventoryKey key = seedBalance(StockStatus.AVAILABLE, 2);
        IdempotentReserveInventoryCommand command = command(key, 3);
        var rejection = new IdempotentReservationResult.Rejected(
                ReservationFailureCode.INSUFFICIENT_STOCK
        );

        assertEquals(rejection, service.reserve(command));
        assertEquals(new Balance(2, 0, 0), balance(key));
        assertTrue(reservationIds(key).isEmpty());
        assertRejectedRow(command, ReservationFailureCode.INSUFFICIENT_STOCK);
        IdempotencyRow original = idempotencyRow(command);

        setOnHandAsOwner(key, 10);
        assertEquals(rejection, service.reserve(command));
        assertEquals(new Balance(10, 0, 0), balance(key));
        assertTrue(reservationIds(key).isEmpty());
        assertEquals(original, idempotencyRow(command));
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));

        var newAttempt = command(key, 3);
        var success = assertInstanceOf(
                IdempotentReservationResult.Succeeded.class, service.reserve(newAttempt)
        );
        assertEquals(new Balance(10, 3, 1), balance(key));
        assertEquals(java.util.List.of(success.reservationId()), reservationIds(key));
        assertSucceededRow(newAttempt, success);
        assertEquals(original, idempotencyRow(command));
    }

    @Test
    void quarantinedInventoryClaimsThenCommitsNonReservableRejection() {
        InventoryKey key = seedBalance(StockStatus.QUARANTINED, 10);
        IdempotentReserveInventoryCommand command = command(key, 1);

        assertEquals(
                new IdempotentReservationResult.Rejected(
                        ReservationFailureCode.INVENTORY_NOT_RESERVABLE
                ),
                service.reserve(command)
        );
        assertEquals(new Balance(10, 0, 0), balance(key));
        assertTrue(reservationIds(key).isEmpty());
        assertRejectedRow(command, ReservationFailureCode.INVENTORY_NOT_RESERVABLE);
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
    }

    @Test
    void missingInventoryIsTerminalBusinessRejection() {
        InventoryKey key = new InventoryKey(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), StockStatus.AVAILABLE
        );
        IdempotentReserveInventoryCommand command = command(key, 1);

        assertEquals(
                new IdempotentReservationResult.Rejected(
                        ReservationFailureCode.INVENTORY_NOT_FOUND
                ),
                service.reserve(command)
        );
        assertTrue(reservationIds(key).isEmpty());
        assertRejectedRow(command, ReservationFailureCode.INVENTORY_NOT_FOUND);
    }

    @Test
    void malformedCommandsDoNotClaimOrMutateAndCorrectedQuantityCanUseSameKey() {
        InventoryKey key = seedBalance(StockStatus.AVAILABLE, 10);
        long initialIdempotencyRows = totalIdempotencyCount();
        String overLimit = "🙂".repeat(129);
        String keyForQuantity = UUID.randomUUID().toString();
        IdempotentReserveInventoryCommand[] malformed = {
                null,
                new IdempotentReserveInventoryCommand(null, "null-actor", key, 1),
                new IdempotentReserveInventoryCommand("  ", "blank-actor", key, 1),
                new IdempotentReserveInventoryCommand(overLimit, "long-actor", key, 1),
                new IdempotentReserveInventoryCommand(ACTOR, null, key, 1),
                new IdempotentReserveInventoryCommand(ACTOR, "  ", key, 1),
                new IdempotentReserveInventoryCommand(ACTOR, overLimit, key, 1),
                new IdempotentReserveInventoryCommand(ACTOR, "null-inventory", null, 1)
        };
        for (IdempotentReserveInventoryCommand invalid : malformed) {
            observer.reset();
            assertThrows(IllegalArgumentException.class, () -> service.reserve(invalid));
            assertNull(observer.observation(), "pre-validation must not call repository");
            assertEquals(initialIdempotencyRows, totalIdempotencyCount());
            assertEquals(new Balance(10, 0, 0), balance(key));
            assertTrue(reservationIds(key).isEmpty());
        }

        for (long quantity : new long[]{0, -1}) {
            observer.reset();
            var invalid = new IdempotentReserveInventoryCommand(
                    ACTOR, keyForQuantity, key, quantity
            );
            var error = assertThrows(
                    ReservationFailureException.class, () -> service.reserve(invalid)
            );
            assertEquals(ReservationFailureCode.INVALID_QUANTITY, error.code());
            assertNull(observer.observation());
            assertEquals(0L, idempotencyCount(ACTOR, keyForQuantity));
            assertEquals(new Balance(10, 0, 0), balance(key));
            assertTrue(reservationIds(key).isEmpty());
        }

        var corrected = new IdempotentReserveInventoryCommand(
                ACTOR, keyForQuantity, key, 1
        );
        var success = assertInstanceOf(
                IdempotentReservationResult.Succeeded.class, service.reserve(corrected)
        );
        assertEquals(new Balance(10, 1, 1), balance(key));
        assertEquals(java.util.List.of(success.reservationId()), reservationIds(key));
        assertSucceededRow(corrected, success);
    }

    @Test
    void identityStringsRemainExactAfterValidation() {
        InventoryKey key = seedBalance(StockStatus.AVAILABLE, 10);
        var command = new IdempotentReserveInventoryCommand(
                " " + ACTOR + " ", " opaque-key ", key, 1
        );
        var success = assertInstanceOf(
                IdempotentReservationResult.Succeeded.class, service.reserve(command)
        );
        assertSucceededRow(command, success);
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
        assertEquals(0L, idempotencyCount(ACTOR, "opaque-key"));
    }

    @Test
    void resultLossRetryReturnsOriginalCommittedReservation() {
        InventoryKey key = seedBalance(StockStatus.AVAILABLE, 10);
        IdempotentReserveInventoryCommand command = command(key, 2);

        service.reserve(command); // Simulate application caller losing the returned result.
        IdempotencyRow committed = idempotencyRow(command);
        UUID originalReservationId = committed.reservationId();
        Long originalVersion = committed.inventoryVersion();
        assertNotNull(originalReservationId);
        assertNotNull(originalVersion);

        assertEquals(
                new IdempotentReservationResult.Succeeded(
                        originalReservationId, originalVersion
                ),
                service.reserve(command)
        );
        assertEquals(new Balance(10, 2, 1), balance(key));
        assertEquals(java.util.List.of(originalReservationId), reservationIds(key));
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
        assertEquals(committed, idempotencyRow(command));
    }

    @Test
    void injectedSaveFailureRollsBackClaimAndStockThenSameKeyCanSucceed() {
        InventoryKey key = seedBalance(StockStatus.AVAILABLE, 10);
        IdempotentReserveInventoryCommand command = command(key, 2);
        failureInjector.arm(command.actorScope(), command.idempotencyKey());

        assertThrows(InjectedSaveFailure.class, () -> service.reserve(command));
        assertEquals(new SaveObservation(2, 1, 1), failureInjector.observation());
        assertEquals(new Balance(10, 0, 0), balance(key));
        assertTrue(reservationIds(key).isEmpty());
        assertEquals(0L, idempotencyCount(command.actorScope(), command.idempotencyKey()));

        var success = assertInstanceOf(
                IdempotentReservationResult.Succeeded.class, service.reserve(command)
        );
        assertEquals(new Balance(10, 2, 1), balance(key));
        assertEquals(java.util.List.of(success.reservationId()), reservationIds(key));
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
        assertSucceededRow(command, success);
    }

    private Balance balance(InventoryKey key) {
        return jdbcClient.sql("""
                SELECT on_hand, reserved, version FROM vra.inventory_balance
                WHERE sku_id = :sku AND owner_id = :owner
                  AND location_id = :location AND stock_status = :status
                """)
                .param("sku", key.skuId())
                .param("owner", key.ownerId())
                .param("location", key.locationId())
                .param("status", key.stockStatus().name())
                .query((result, rowNumber) -> new Balance(
                        result.getLong("on_hand"),
                        result.getLong("reserved"),
                        result.getLong("version")
                ))
                .single();
    }

    private java.util.List<UUID> reservationIds(InventoryKey key) {
        return jdbcClient.sql("""
                SELECT reservation_id FROM vra.inventory_reservation
                WHERE sku_id = :sku AND owner_id = :owner
                  AND location_id = :location AND stock_status = :status
                """)
                .param("sku", key.skuId())
                .param("owner", key.ownerId())
                .param("location", key.locationId())
                .param("status", key.stockStatus().name())
                .query(UUID.class)
                .list();
    }

    private long idempotencyCount(String actorScope, String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT count(*) FROM vra.inventory_reservation_idempotency
                WHERE actor_scope = :actor AND idempotency_key = :key
                """)
                .param("actor", actorScope)
                .param("key", idempotencyKey)
                .query(Long.class)
                .single();
    }

    private long totalIdempotencyCount() {
        return jdbcClient.sql("""
                SELECT count(*) FROM vra.inventory_reservation_idempotency
                """).query(Long.class).single();
    }

    private IdempotencyRow idempotencyRow(IdempotentReserveInventoryCommand command) {
        return jdbcClient.sql("""
                SELECT fingerprint_version, request_fingerprint, outcome_status,
                       reservation_id, inventory_version, rejection_code,
                       created_at, completed_at
                FROM vra.inventory_reservation_idempotency
                WHERE actor_scope = :actor AND idempotency_key = :key
                """)
                .param("actor", command.actorScope())
                .param("key", command.idempotencyKey())
                .query((result, rowNumber) -> new IdempotencyRow(
                        result.getShort("fingerprint_version"),
                        result.getString("request_fingerprint"),
                        result.getString("outcome_status"),
                        result.getObject("reservation_id", UUID.class),
                        result.getObject("inventory_version", Long.class),
                        result.getString("rejection_code"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("completed_at") == null
                                ? null : result.getTimestamp("completed_at").toInstant()
                ))
                .single();
    }

    private void assertSucceededRow(
            IdempotentReserveInventoryCommand command,
            IdempotentReservationResult.Succeeded success
    ) {
        IdempotencyRow row = idempotencyRow(command);
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
        assertEquals(ReservationRequestFingerprint.VERSION, row.fingerprintVersion());
        assertEquals(fingerprint.compute(command.inventoryKey(), command.quantity()).value(),
                row.requestFingerprint());
        assertEquals("SUCCEEDED", row.outcomeStatus());
        assertEquals(success.reservationId(), row.reservationId());
        assertEquals(success.inventoryVersion(), row.inventoryVersion());
        assertNull(row.rejectionCode());
        assertNotNull(row.createdAt());
        assertNotNull(row.completedAt());
    }

    private void assertRejectedRow(
            IdempotentReserveInventoryCommand command,
            ReservationFailureCode code
    ) {
        IdempotencyRow row = idempotencyRow(command);
        assertEquals(1L, idempotencyCount(command.actorScope(), command.idempotencyKey()));
        assertEquals(ReservationRequestFingerprint.VERSION, row.fingerprintVersion());
        assertEquals(fingerprint.compute(command.inventoryKey(), command.quantity()).value(),
                row.requestFingerprint());
        assertEquals("REJECTED", row.outcomeStatus());
        assertNull(row.reservationId());
        assertNull(row.inventoryVersion());
        assertEquals(code.name(), row.rejectionCode());
        assertNotNull(row.createdAt());
        assertNotNull(row.completedAt());
    }

    private static void setOnHandAsOwner(InventoryKey key, long onHand) {
        try (Connection connection = adminConnection();
             Statement role = connection.createStatement()) {
            role.execute("SET ROLE vra_owner");
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE vra.inventory_balance SET on_hand = ?
                    WHERE sku_id = ? AND owner_id = ? AND location_id = ?
                      AND stock_status = ?
                    """)) {
                update.setLong(1, onHand);
                update.setObject(2, key.skuId());
                update.setObject(3, key.ownerId());
                update.setObject(4, key.locationId());
                update.setString(5, key.stockStatus().name());
                assertEquals(1, update.executeUpdate());
            }
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private record Balance(long onHand, long reserved, long version) {
    }

    private record IdempotencyRow(
            short fingerprintVersion,
            String requestFingerprint,
            String outcomeStatus,
            UUID reservationId,
            Long inventoryVersion,
            String rejectionCode,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    @AfterEach
    void resetDecoratorsAndAssertNoCommittedIncompleteClaims() {
        observer.reset();
        failureInjector.reset();
        assertEquals(0L, jdbcClient.sql("""
                SELECT count(*) FROM vra.inventory_reservation_idempotency
                WHERE outcome_status IS NULL
                """).query(Long.class).single());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RepositoryObservers {
        @Bean
        @Primary
        ObservingIdempotencyRepository observingIdempotencyRepository(
                JdbcReservationIdempotencyRepository delegate, JdbcClient jdbcClient) {
            return new ObservingIdempotencyRepository(delegate, jdbcClient);
        }

        @Bean
        @Primary
        FaultInjectingReservationRepository faultInjectingReservationRepository(
                JpaReservationRepository delegate, JdbcClient jdbcClient) {
            return new FaultInjectingReservationRepository(delegate, jdbcClient);
        }
    }

    static final class ObservingIdempotencyRepository implements ReservationIdempotencyRepository {
        private final JdbcReservationIdempotencyRepository delegate;
        private final JdbcClient jdbcClient;
        private final AtomicReference<Observation> observation = new AtomicReference<>();

        ObservingIdempotencyRepository(
                JdbcReservationIdempotencyRepository delegate, JdbcClient jdbcClient) {
            this.delegate = delegate;
            this.jdbcClient = jdbcClient;
        }

        Observation observation() {
            return observation.get();
        }

        void reset() {
            observation.set(null);
        }

        @Override
        public ClaimResult claimOrResolve(String actorScope, String idempotencyKey,
                                          ReservationRequestFingerprint.Fingerprint fingerprint,
                                          Instant createdAt) {
            observation.set(new Observation(
                    jdbcClient.sql("SHOW transaction_isolation").query(String.class).single(),
                    jdbcClient.sql("SELECT current_user").query(String.class).single()));
            return delegate.claimOrResolve(actorScope, idempotencyKey, fingerprint, createdAt);
        }

        @Override
        public void completeSucceeded(String actorScope, String idempotencyKey,
                                      ReservationRequestFingerprint.Fingerprint fingerprint,
                                      UUID reservationId, long inventoryVersion, Instant completedAt) {
            delegate.completeSucceeded(actorScope, idempotencyKey, fingerprint,
                    reservationId, inventoryVersion, completedAt);
        }

        @Override
        public void completeRejected(String actorScope, String idempotencyKey,
                                     ReservationRequestFingerprint.Fingerprint fingerprint,
                                     ReservationFailureCode rejectionCode, Instant completedAt) {
            delegate.completeRejected(actorScope, idempotencyKey, fingerprint,
                    rejectionCode, completedAt);
        }
    }

    private record Observation(String isolation, String databaseUser) {}

    static final class FaultInjectingReservationRepository implements ReservationRepository {
        private final JpaReservationRepository delegate;
        private final JdbcClient jdbcClient;
        private final AtomicBoolean failNext = new AtomicBoolean();
        private volatile String actorScope;
        private volatile String idempotencyKey;
        private volatile SaveObservation observation;

        FaultInjectingReservationRepository(JpaReservationRepository delegate, JdbcClient jdbcClient) {
            this.delegate = delegate;
            this.jdbcClient = jdbcClient;
        }

        void arm(String actorScope, String idempotencyKey) {
            this.actorScope = actorScope;
            this.idempotencyKey = idempotencyKey;
            observation = null;
            failNext.set(true);
        }

        SaveObservation observation() {
            return observation;
        }

        void reset() {
            failNext.set(false);
            actorScope = null;
            idempotencyKey = null;
            observation = null;
        }

        @Override
        public void save(InventoryReservation reservation) {
            if (failNext.getAndSet(false)) {
                InventoryKey key = reservation.inventoryKey();
                var inventory = jdbcClient.sql("""
                        SELECT reserved, version FROM vra.inventory_balance
                        WHERE sku_id = :sku AND owner_id = :owner
                          AND location_id = :location AND stock_status = :status
                        """).param("sku", key.skuId()).param("owner", key.ownerId())
                        .param("location", key.locationId())
                        .param("status", key.stockStatus().name())
                        .query((rs, rowNumber) ->
                                new long[]{rs.getLong("reserved"), rs.getLong("version")}).single();
                long claims = jdbcClient.sql("""
                        SELECT count(*) FROM vra.inventory_reservation_idempotency
                        WHERE actor_scope = :actor AND idempotency_key = :key
                          AND outcome_status IS NULL
                        """).param("actor", actorScope).param("key", idempotencyKey)
                        .query(Long.class).single();
                observation = new SaveObservation(inventory[0], inventory[1], claims);
                throw new InjectedSaveFailure();
            }
            delegate.save(reservation);
        }
    }

    private record SaveObservation(long reserved, long version, long transientClaims) {}

    static final class InjectedSaveFailure extends RuntimeException {
        InjectedSaveFailure() {
            super("test-only reservation save failure");
        }
    }

    private static IdempotentReserveInventoryCommand command(InventoryKey key, long quantity) {
        return new IdempotentReserveInventoryCommand(
                ACTOR, UUID.randomUUID().toString(), key, quantity);
    }

    private static InventoryKey seedBalance(StockStatus status, long onHand) {
        InventoryKey key = new InventoryKey(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status);
        try (Connection connection = adminConnection();
             Statement role = connection.createStatement()) {
            role.execute("SET ROLE vra_owner");
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO vra.inventory_balance (
                        sku_id, owner_id, location_id, stock_status,
                        on_hand, reserved, version
                    ) VALUES (?, ?, ?, ?, ?, 0, 0)
                    """)) {
                insert.setObject(1, key.skuId());
                insert.setObject(2, key.ownerId());
                insert.setObject(3, key.locationId());
                insert.setString(4, status.name());
                insert.setLong(5, onHand);
                assertEquals(1, insert.executeUpdate());
            }
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
        return key;
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
