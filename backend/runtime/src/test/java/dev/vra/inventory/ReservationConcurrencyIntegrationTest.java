package dev.vra.inventory;

import dev.vra.async.AsyncRoleBootstrap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.zaxxer.hikari.HikariDataSource;
import dev.vra.inventory.adapter.out.persistence.JdbcInventoryBalanceRepository;
import dev.vra.inventory.application.IdempotentReservationApplicationService;
import dev.vra.inventory.application.IdempotentReservationResult;
import dev.vra.inventory.application.IdempotentReservationResult.IdempotencyFailureCode;
import dev.vra.inventory.application.IdempotentReserveInventoryCommand;
import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationRequestFingerprint;
import dev.vra.inventory.application.port.InventoryBalanceRepository;
import dev.vra.inventory.domain.InventoryKey;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
@Import(ReservationConcurrencyIntegrationTest.ConcurrencyTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReservationConcurrencyIntegrationTest {
    private static final int WORKER_COUNT = 16;
    private static final int DB_POOL_MAX = 8;
    private static final Duration SCENARIO_TIMEOUT = Duration.ofSeconds(120);
    private static final String DATABASE = "vra_concurrency_test";
    private static final String ADMIN_USER = "postgres";
    private static final String ADMIN_PASSWORD = "concurrency-admin-test-only";
    private static final String MIGRATOR_USER = "vra_migrator";
    private static final String MIGRATOR_PASSWORD = "concurrency-migrator-test-only";
    private static final String RUNTIME_USER = "vra_runtime";
    private static final String RUNTIME_PASSWORD = "concurrency-runtime-test-only";

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
                    POSTGRES.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD
            );
            if (migrated != 4) {
                throw new IllegalStateException("Expected exactly four migrations, got " + migrated);
            }
        } catch (Exception error) {
            POSTGRES.stop();
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void runtimeDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("vra.database.url", POSTGRES::getJdbcUrl);
        registry.add("vra.database.username", () -> RUNTIME_USER);
        registry.add("vra.database.password", () -> RUNTIME_PASSWORD);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyTestConfiguration {
        @Bean(destroyMethod = "close")
        @Primary
        HikariDataSource concurrencyDataSource() {
            HikariDataSource pool = new HikariDataSource();
            pool.setJdbcUrl(POSTGRES.getJdbcUrl());
            pool.setUsername(RUNTIME_USER);
            pool.setPassword(RUNTIME_PASSWORD);
            pool.setMaximumPoolSize(DB_POOL_MAX);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(30_000);
            pool.setPoolName("vra-gate5-test-pool");
            return pool;
        }

        @Bean
        @Primary
        CountingInventoryBalanceRepository countingInventoryBalanceRepository(
                JdbcInventoryBalanceRepository delegate
        ) {
            return new CountingInventoryBalanceRepository(delegate);
        }
    }

    static final class CountingInventoryBalanceRepository implements InventoryBalanceRepository {
        private final JdbcInventoryBalanceRepository delegate;
        private final AtomicInteger calls = new AtomicInteger();

        CountingInventoryBalanceRepository(JdbcInventoryBalanceRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public ReserveAttempt reserve(InventoryKey key, long quantity) {
            calls.incrementAndGet();
            return delegate.reserve(key, quantity);
        }

        int count() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }

    @Autowired
    private IdempotentReservationApplicationService service;
    @Autowired
    private ReservationRequestFingerprint fingerprint;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private HikariDataSource dataSource;
    @Autowired
    private CountingInventoryBalanceRepository executionCounter;

    @AfterEach
    void noCommittedIncompleteIdempotencyRows() {
        assertEquals(0L, committedIncompleteCount());
    }

    @Test
    void oneUnitAcrossFiveHundredDistinctIdentities() {
        assertPoolConfiguration();
        executionCounter.reset();
        InventoryKey key = seedBalance(1, 0, 0);
        String actor = "stock-" + UUID.randomUUID();
        List<Supplier<IdempotentReservationResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            var command = new IdempotentReserveInventoryCommand(
                    actor, UUID.randomUUID().toString(), key, 1
            );
            attempts.add(() -> service.reserve(command));
        }

        Run<IdempotentReservationResult> run = runConcurrent(attempts);
        BusinessCounts counts = businessCounts(run.results());
        Balance balance = balance(key);
        List<ReservationRow> reservations = reservations(key);
        IdempotencyCounts rows = idempotencyCounts(actor, fingerprint.compute(key, 1).value());
        System.out.println("GATE5 stock500 " + run.diagnostic()
                + " results=" + counts + " executions=" + executionCounter.count()
                + " balance=" + balance + " reservations=" + reservations
                + " idempotency=" + rows);

        assertHealthy(run);
        assertEquals(500, run.logicalAttempts());
        assertEquals(1, counts.success());
        assertEquals(499L, counts.rejections().getOrDefault(
                ReservationFailureCode.INSUFFICIENT_STOCK, 0L));
        assertEquals(499, counts.totalRejected());
        assertEquals(0, counts.conflict());
        assertEquals(500, executionCounter.count());
        assertEquals(new Balance(1, 1, 1), balance);
        assertTrue(balance.onHand() - balance.reserved() >= 0);
        assertEquals(1, reservations.size());
        assertEquals(1L, reservations.getFirst().quantity());
        var success = successes(run.results()).getFirst();
        assertEquals(reservations.getFirst().reservationId(), success.reservationId());
        assertEquals(1L, success.inventoryVersion());
        assertEquals(new IdempotencyCounts(500, 1, 499, 0, 499, 0, 500, 500), rows);
        IdempotencyRow winner = succeededRow(actor);
        assertEquals(success.reservationId(), winner.reservationId());
        assertEquals(1L, winner.inventoryVersion());
        assertEquals(0L, committedIncompleteCount());
    }

    @Test
    void concurrentSameKeyReturnsOneAuthoritativeResult() {
        assertPoolConfiguration();
        executionCounter.reset();
        InventoryKey key = seedBalance(10, 0, 0);
        String actor = "same-key-" + UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        var command = new IdempotentReserveInventoryCommand(actor, idempotencyKey, key, 1);
        List<Supplier<IdempotentReservationResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            attempts.add(() -> service.reserve(command));
        }

        Run<IdempotentReservationResult> run = runConcurrent(attempts);
        BusinessCounts counts = businessCounts(run.results());
        Balance balance = balance(key);
        List<ReservationRow> reservations = reservations(key);
        IdempotencyRow row = idempotencyRow(actor, idempotencyKey);
        System.out.println("GATE5 sameKey64 " + run.diagnostic()
                + " results=" + counts + " executions=" + executionCounter.count()
                + " balance=" + balance + " reservations=" + reservations
                + " idempotency=" + row);

        assertHealthy(run);
        assertEquals(64, counts.success());
        assertEquals(0, counts.totalRejected());
        assertEquals(0, counts.conflict());
        assertEquals(1, executionCounter.count());
        var original = successes(run.results()).getFirst();
        assertEquals(1L, original.inventoryVersion());
        assertTrue(successes(run.results()).stream().allMatch(original::equals));
        assertEquals(new Balance(10, 1, 1), balance);
        assertEquals(1, reservations.size());
        assertEquals(original.reservationId(), reservations.getFirst().reservationId());
        assertEquals(1L, idempotencyCount(actor, idempotencyKey));
        assertEquals("SUCCEEDED", row.outcomeStatus());
        assertEquals(original.reservationId(), row.reservationId());
        assertEquals(1L, row.inventoryVersion());
        assertEquals(0L, committedIncompleteCount());
    }

    @Test
    void concurrentConflictingPayloadsBindExactlyOneFingerprint() {
        assertPoolConfiguration();
        executionCounter.reset();
        InventoryKey key = seedBalance(10, 0, 0);
        String actor = "conflict-" + UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        var first = new IdempotentReserveInventoryCommand(actor, idempotencyKey, key, 1);
        var second = new IdempotentReserveInventoryCommand(actor, idempotencyKey, key, 2);
        Run<IdempotentReservationResult> run = runConcurrent(List.of(
                () -> service.reserve(first), () -> service.reserve(second)
        ));
        BusinessCounts counts = businessCounts(run.results());
        Balance balance = balance(key);
        List<ReservationRow> reservations = reservations(key);
        IdempotencyRow row = idempotencyRow(actor, idempotencyKey);
        System.out.println("GATE5 conflictingPayloads " + run.diagnostic()
                + " results=" + counts + " returned=" + run.results()
                + " executions=" + executionCounter.count() + " balance=" + balance
                + " reservations=" + reservations + " idempotency=" + row);

        assertHealthy(run);
        assertEquals(1, counts.success());
        assertEquals(1, counts.conflict());
        assertEquals(0, counts.totalRejected());
        assertEquals(1, executionCounter.count());
        int winnerIndex = run.results().get(0) instanceof IdempotentReservationResult.Succeeded
                ? 0 : 1;
        long winningQuantity = winnerIndex == 0 ? 1 : 2;
        var winner = successes(run.results()).getFirst();
        assertEquals(new IdempotentReservationResult.Conflict(
                IdempotencyFailureCode.IDEMPOTENCY_KEY_REUSED),
                run.results().get(1 - winnerIndex));
        assertEquals(new Balance(10, winningQuantity, 1), balance);
        assertEquals(1, reservations.size());
        assertEquals(winningQuantity, reservations.getFirst().quantity());
        assertEquals(winner.reservationId(), reservations.getFirst().reservationId());
        assertEquals(1L, idempotencyCount(actor, idempotencyKey));
        assertEquals("SUCCEEDED", row.outcomeStatus());
        assertEquals(winner.reservationId(), row.reservationId());
        assertEquals(1L, row.inventoryVersion());
        String firstFingerprint = fingerprint.compute(key, 1).value();
        String secondFingerprint = fingerprint.compute(key, 2).value();
        assertTrue(row.requestFingerprint().equals(firstFingerprint)
                || row.requestFingerprint().equals(secondFingerprint));
        assertEquals(winnerIndex == 0 ? firstFingerprint : secondFingerprint,
                row.requestFingerprint());
        assertEquals(0L, committedIncompleteCount());
    }

    @Test
    void sameKeyInDifferentActorScopesExecutesTwice() {
        assertPoolConfiguration();
        executionCounter.reset();
        InventoryKey key = seedBalance(2, 0, 0);
        String suffix = UUID.randomUUID().toString();
        String actorA = "buyer-A-" + suffix;
        String actorB = "buyer-B-" + suffix;
        String idempotencyKey = UUID.randomUUID().toString();
        Run<IdempotentReservationResult> run = runConcurrent(List.of(
                () -> service.reserve(new IdempotentReserveInventoryCommand(
                        actorA, idempotencyKey, key, 1)),
                () -> service.reserve(new IdempotentReserveInventoryCommand(
                        actorB, idempotencyKey, key, 1))
        ));
        BusinessCounts counts = businessCounts(run.results());
        Balance balance = balance(key);
        List<ReservationRow> reservations = reservations(key);
        IdempotencyRow rowA = idempotencyRow(actorA, idempotencyKey);
        IdempotencyRow rowB = idempotencyRow(actorB, idempotencyKey);
        System.out.println("GATE5 scopeIsolation " + run.diagnostic()
                + " results=" + counts + " executions=" + executionCounter.count()
                + " balance=" + balance + " reservations=" + reservations
                + " rows=" + List.of(rowA, rowB));

        assertHealthy(run);
        assertEquals(2, counts.success());
        assertEquals(0, counts.totalRejected());
        assertEquals(0, counts.conflict());
        assertEquals(2, executionCounter.count());
        var successes = successes(run.results());
        assertNotEquals(successes.get(0).reservationId(), successes.get(1).reservationId());
        assertEquals(new Balance(2, 2, 2), balance);
        assertEquals(2, reservations.size());
        Set<UUID> persistedIds = Set.of(
                reservations.get(0).reservationId(), reservations.get(1).reservationId()
        );
        assertEquals(Set.of(successes.get(0).reservationId(), successes.get(1).reservationId()),
                persistedIds);
        assertEquals(1L, idempotencyCount(actorA, idempotencyKey));
        assertEquals(1L, idempotencyCount(actorB, idempotencyKey));
        assertEquals(2L, scopedIdempotencyCount(actorA, actorB, idempotencyKey));
        assertEquals("SUCCEEDED", rowA.outcomeStatus());
        assertEquals("SUCCEEDED", rowB.outcomeStatus());
        assertEquals(0L, committedIncompleteCount());
    }

    @Test
    void concurrentOptimisticCompareAndSwapIsSeparateFromReservations() {
        assertPoolConfiguration();
        executionCounter.reset();
        InventoryKey key = seedBalance(10, 2, 10);
        long idempotencyRowsBefore = totalIdempotencyCount();
        Supplier<Integer> staleUpdate = () -> jdbcClient.sql("""
                UPDATE vra.inventory_balance SET version = version + 1
                WHERE sku_id = :sku AND owner_id = :owner
                  AND location_id = :location AND stock_status = :status
                  AND version = :expectedVersion
                """)
                .param("sku", key.skuId())
                .param("owner", key.ownerId())
                .param("location", key.locationId())
                .param("status", key.stockStatus().name())
                .param("expectedVersion", 10)
                .update();
        Run<Integer> run = runConcurrent(List.of(staleUpdate, staleUpdate));
        Balance balance = balance(key);
        List<ReservationRow> reservations = reservations(key);
        System.out.println("GATE5 optimisticCAS " + run.diagnostic()
                + " affectedRows=" + run.results() + " executions=" + executionCounter.count()
                + " balance=" + balance + " reservations=" + reservations);

        assertHealthy(run);
        assertEquals(2, run.results().size());
        assertEquals(1L, run.results().stream().filter(value -> value == 1).count());
        assertEquals(1L, run.results().stream().filter(value -> value == 0).count());
        assertEquals(new Balance(10, 2, 11), balance);
        assertTrue(reservations.isEmpty());
        assertEquals(0, executionCounter.count());
        assertEquals(idempotencyRowsBefore, totalIdempotencyCount());
        assertEquals(0L, committedIncompleteCount());
    }

    private void assertPoolConfiguration() {
        assertEquals(DB_POOL_MAX, dataSource.getMaximumPoolSize());
        assertEquals(0, dataSource.getMinimumIdle());
        assertEquals(30_000, dataSource.getConnectionTimeout());
        assertEquals(RUNTIME_USER, jdbcClient.sql("SELECT current_user").query(String.class).single());
    }

    private TransactionTemplate workerTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(30);
        return template;
    }

    private <T> Run<T> runConcurrent(List<Supplier<T>> operations) {
        int logicalAttempts = operations.size();
        CountDownLatch ready = new CountDownLatch(Math.min(WORKER_COUNT, logicalAttempts));
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch overlap = new CountDownLatch(2);
        ConcurrentLinkedQueue<Integer> pids = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        List<Future<Attempt<T>>> futures = new ArrayList<>();
        long started = System.nanoTime();
        long deadline = started + SCENARIO_TIMEOUT.toNanos();
        List<T> results = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        int timeouts = 0;
        int deadlocks = 0;
        try {
            for (Supplier<T> operation : operations) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        awaitLatch(start, 120, "start gate");
                        T result = workerTransaction().execute(status -> {
                            TxIdentity identity = jdbcClient.sql("""
                                    SELECT current_setting('transaction_isolation'),
                                           pg_backend_pid(), current_user
                                    """).query((row, index) -> new TxIdentity(
                                    row.getString(1), row.getInt(2), row.getString(3)
                            )).single();
                            assertEquals("read committed", identity.isolation());
                            assertEquals(RUNTIME_USER, identity.databaseUser());
                            pids.add(identity.backendPid());
                            overlap.countDown();
                            awaitLatch(overlap, 10, "PostgreSQL backend overlap");
                            return operation.get();
                        });
                        return new Attempt<>(result, null);
                    } catch (Throwable error) {
                        // Catch outside TransactionTemplate so infrastructure errors roll back.
                        return new Attempt<>(null, error);
                    }
                }));
            }
            awaitLatch(ready, 10, "ready workers");
            start.countDown();
            for (Future<Attempt<T>> future : futures) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    timeouts++;
                    future.cancel(true);
                    continue;
                }
                try {
                    Attempt<T> attempt = future.get(remaining, TimeUnit.NANOSECONDS);
                    if (attempt.error() == null) {
                        results.add(attempt.result());
                    } else {
                        errors.add(attempt.error());
                        if (hasSqlState(attempt.error(), "40P01")) {
                            deadlocks++;
                        }
                        if (hasSqlState(attempt.error(), "57014")
                                || hasSqlState(attempt.error(), "55P03")
                                || attempt.error() instanceof TimeoutException) {
                            timeouts++;
                        }
                    }
                } catch (TimeoutException timeout) {
                    timeouts++;
                    future.cancel(true);
                    errors.add(timeout);
                } catch (ExecutionException error) {
                    errors.add(error.getCause());
                    if (hasSqlState(error.getCause(), "40P01")) {
                        deadlocks++;
                    }
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            errors.add(error);
        } finally {
            start.countDown();
            if (timeouts != 0 || !errors.isEmpty()) {
                for (Future<Attempt<T>> future : futures) {
                    if (!future.isDone()) {
                        future.cancel(true);
                    }
                }
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    errors.add(new IllegalStateException("Executor did not terminate"));
                    executor.shutdownNow();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                errors.add(error);
                executor.shutdownNow();
            }
        }
        return new Run<>(logicalAttempts, List.copyOf(results), List.copyOf(errors),
                timeouts, deadlocks, overlap.getCount(), new HashSet<>(pids),
                Duration.ofNanos(System.nanoTime() - started));
    }

    private static void awaitLatch(CountDownLatch latch, int timeoutSeconds, String name) {
        try {
            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for " + name);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for " + name, error);
        }
    }

    private static boolean hasSqlState(Throwable error, String expected) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && expected.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static void assertHealthy(Run<?> run) {
        assertEquals(0, run.unexpected().size(), run.diagnostic());
        assertEquals(0, run.timeouts(), run.diagnostic());
        assertEquals(0, run.deadlocks(), run.diagnostic());
        assertEquals(0L, run.overlapRemaining(), run.diagnostic());
        assertTrue(run.backendPids().size() >= 2, run.diagnostic());
        assertEquals(run.logicalAttempts(), run.results().size(), run.diagnostic());
    }

    private static BusinessCounts businessCounts(List<IdempotentReservationResult> results) {
        int success = 0;
        int conflict = 0;
        java.util.EnumMap<ReservationFailureCode, Long> rejected =
                new java.util.EnumMap<>(ReservationFailureCode.class);
        for (IdempotentReservationResult result : results) {
            if (result instanceof IdempotentReservationResult.Succeeded) {
                success++;
            } else if (result instanceof IdempotentReservationResult.Rejected rejection) {
                rejected.merge(rejection.code(), 1L, Long::sum);
            } else if (result instanceof IdempotentReservationResult.Conflict) {
                conflict++;
            } else {
                throw new IllegalStateException("Unknown business result");
            }
        }
        return new BusinessCounts(success, rejected, conflict);
    }

    private static List<IdempotentReservationResult.Succeeded> successes(
            List<IdempotentReservationResult> results
    ) {
        return results.stream()
                .filter(IdempotentReservationResult.Succeeded.class::isInstance)
                .map(IdempotentReservationResult.Succeeded.class::cast)
                .toList();
    }

    private record Attempt<T>(T result, Throwable error) {
    }

    private record TxIdentity(String isolation, int backendPid, String databaseUser) {
    }

    private record Run<T>(int logicalAttempts, List<T> results, List<Throwable> unexpected,
                          int timeouts, int deadlocks, long overlapRemaining,
                          Set<Integer> backendPids, Duration elapsed) {
        String diagnostic() {
            return "attempts=" + logicalAttempts + " workers=" + WORKER_COUNT
                    + " poolMax=" + DB_POOL_MAX + " pids=" + backendPids
                    + " unexpected=" + unexpected.stream()
                    .map(error -> error.getClass().getName() + ":" + error.getMessage()
                            + " sqlState=" + rootSqlState(error))
                    .toList()
                    + " timeouts=" + timeouts + " deadlocks=" + deadlocks
                    + " overlapRemaining=" + overlapRemaining + " elapsed=" + elapsed;
        }
    }

    private static String rootSqlState(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return "none";
    }

    private record BusinessCounts(int success,
                                  java.util.Map<ReservationFailureCode, Long> rejections,
                                  int conflict) {
        int totalRejected() {
            return Math.toIntExact(rejections.values().stream().mapToLong(Long::longValue).sum());
        }
    }

    private Balance balance(InventoryKey key) {
        return jdbcClient.sql("""
                SELECT on_hand, reserved, version FROM vra.inventory_balance
                WHERE sku_id = :sku AND owner_id = :owner
                  AND location_id = :location AND stock_status = :status
                """)
                .param("sku", key.skuId()).param("owner", key.ownerId())
                .param("location", key.locationId())
                .param("status", key.stockStatus().name())
                .query((row, index) -> new Balance(
                        row.getLong("on_hand"), row.getLong("reserved"),
                        row.getLong("version"))).single();
    }

    private List<ReservationRow> reservations(InventoryKey key) {
        return jdbcClient.sql("""
                SELECT reservation_id, quantity FROM vra.inventory_reservation
                WHERE sku_id = :sku AND owner_id = :owner
                  AND location_id = :location AND stock_status = :status
                """)
                .param("sku", key.skuId()).param("owner", key.ownerId())
                .param("location", key.locationId())
                .param("status", key.stockStatus().name())
                .query((row, index) -> new ReservationRow(
                        row.getObject("reservation_id", UUID.class),
                        row.getLong("quantity"))).list();
    }

    private IdempotencyCounts idempotencyCounts(String actor, String expectedFingerprint) {
        return jdbcClient.sql("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE outcome_status = 'SUCCEEDED') AS succeeded,
                       count(*) FILTER (WHERE outcome_status = 'REJECTED') AS rejected,
                       count(*) FILTER (WHERE outcome_status IS NULL) AS incomplete,
                       count(*) FILTER (WHERE rejection_code = 'INSUFFICIENT_STOCK') AS insufficient,
                       count(*) FILTER (WHERE rejection_code IS NOT NULL
                                       AND rejection_code <> 'INSUFFICIENT_STOCK') AS other_rejection,
                       count(*) FILTER (WHERE fingerprint_version = 1) AS version_one,
                       count(*) FILTER (WHERE request_fingerprint = :fingerprint) AS matching_fingerprint
                FROM vra.inventory_reservation_idempotency WHERE actor_scope = :actor
                """).param("actor", actor).param("fingerprint", expectedFingerprint)
                .query((row, index) -> new IdempotencyCounts(
                        row.getLong("total"), row.getLong("succeeded"),
                        row.getLong("rejected"), row.getLong("incomplete"),
                        row.getLong("insufficient"), row.getLong("other_rejection"),
                        row.getLong("version_one"), row.getLong("matching_fingerprint"))).single();
    }

    private IdempotencyRow idempotencyRow(String actor, String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT outcome_status, reservation_id, inventory_version,
                       request_fingerprint, fingerprint_version, rejection_code
                FROM vra.inventory_reservation_idempotency
                WHERE actor_scope = :actor AND idempotency_key = :key
                """).param("actor", actor).param("key", idempotencyKey)
                .query((row, index) -> new IdempotencyRow(
                        row.getString("outcome_status"),
                        row.getObject("reservation_id", UUID.class),
                        row.getObject("inventory_version", Long.class),
                        row.getString("request_fingerprint"),
                        row.getShort("fingerprint_version"),
                        row.getString("rejection_code"))).single();
    }

    private IdempotencyRow succeededRow(String actor) {
        return jdbcClient.sql("""
                SELECT outcome_status, reservation_id, inventory_version,
                       request_fingerprint, fingerprint_version, rejection_code
                FROM vra.inventory_reservation_idempotency
                WHERE actor_scope = :actor AND outcome_status = 'SUCCEEDED'
                """).param("actor", actor)
                .query((row, index) -> new IdempotencyRow(
                        row.getString("outcome_status"),
                        row.getObject("reservation_id", UUID.class),
                        row.getObject("inventory_version", Long.class),
                        row.getString("request_fingerprint"),
                        row.getShort("fingerprint_version"),
                        row.getString("rejection_code"))).single();
    }

    private long idempotencyCount(String actor, String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT count(*) FROM vra.inventory_reservation_idempotency
                WHERE actor_scope = :actor AND idempotency_key = :key
                """).param("actor", actor).param("key", idempotencyKey)
                .query(Long.class).single();
    }

    private long scopedIdempotencyCount(String actorA, String actorB, String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT count(*) FROM vra.inventory_reservation_idempotency
                WHERE actor_scope IN (:actorA, :actorB) AND idempotency_key = :key
                """).param("actorA", actorA).param("actorB", actorB)
                .param("key", idempotencyKey).query(Long.class).single();
    }

    private long totalIdempotencyCount() {
        return jdbcClient.sql("SELECT count(*) FROM vra.inventory_reservation_idempotency")
                .query(Long.class).single();
    }

    private long committedIncompleteCount() {
        return jdbcClient.sql("""
                SELECT count(*) FROM vra.inventory_reservation_idempotency
                WHERE outcome_status IS NULL
                """).query(Long.class).single();
    }

    private record Balance(long onHand, long reserved, long version) {
    }

    private record ReservationRow(UUID reservationId, long quantity) {
    }

    private record IdempotencyCounts(long total, long succeeded, long rejected,
                                     long incomplete, long insufficient, long otherRejection,
                                     long versionOne, long matchingFingerprint) {
    }

    private record IdempotencyRow(String outcomeStatus, UUID reservationId,
                                  Long inventoryVersion, String requestFingerprint,
                                  short fingerprintVersion, String rejectionCode) {
    }

    private static InventoryKey seedBalance(long onHand, long reserved, long version) {
        InventoryKey key = new InventoryKey(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), StockStatus.AVAILABLE);
        try (Connection connection = adminConnection();
             Statement role = connection.createStatement()) {
            role.execute("SET ROLE vra_owner");
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO vra.inventory_balance (
                        sku_id, owner_id, location_id, stock_status,
                        on_hand, reserved, version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setObject(1, key.skuId());
                insert.setObject(2, key.ownerId());
                insert.setObject(3, key.locationId());
                insert.setString(4, key.stockStatus().name());
                insert.setLong(5, onHand);
                insert.setLong(6, reserved);
                insert.setLong(7, version);
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
                    NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
                    """);
            statement.execute("""
                    CREATE ROLE vra_migrator
                    LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
                    PASSWORD 'concurrency-migrator-test-only'
                    """);
            statement.execute("""
                    CREATE ROLE vra_runtime
                    LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
                    PASSWORD 'concurrency-runtime-test-only'
                    """);
            statement.execute("""
                    GRANT vra_owner TO vra_migrator
                    WITH ADMIN FALSE, INHERIT FALSE, SET TRUE
                    """);
            statement.execute("REVOKE CREATE ON DATABASE "
                    + quoteIdentifier(DATABASE) + " FROM PUBLIC");
            statement.execute("GRANT CONNECT ON DATABASE "
                    + quoteIdentifier(DATABASE) + " TO vra_migrator, vra_runtime");
            statement.execute("CREATE SCHEMA vra AUTHORIZATION vra_owner");
            statement.execute("GRANT USAGE ON SCHEMA vra TO vra_runtime");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
