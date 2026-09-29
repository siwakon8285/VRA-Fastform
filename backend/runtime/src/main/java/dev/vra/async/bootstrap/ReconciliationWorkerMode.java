package dev.vra.async.bootstrap;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.UUID;

import javax.sql.DataSource;

import dev.vra.async.adapter.out.external.ValidationSimulatorHttpAdapter;
import dev.vra.async.adapter.out.reconciliation.JdbcReconciliationRepository;
import dev.vra.async.application.external.ExternalEffectPort;
import dev.vra.async.application.reconciliation.ReconciliationPolicy;
import dev.vra.async.application.reconciliation.ReconciliationPort;
import dev.vra.async.application.reconciliation.ReconciliationService;
import dev.vra.platform.configuration.RuntimeDatabaseProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Isolated non-web composition for the reconciliation credential and observation adapter. */
public final class ReconciliationWorkerMode {
    private ReconciliationWorkerMode() {}

    @EnableTransactionManagement
    static class TransactionBoundary {}

    public static AnnotationConfigApplicationContext start(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        RuntimeDatabaseProperties database = new RuntimeDatabaseProperties(
                environment.get("VRA_ASYNC_DB_URL"), environment.get("VRA_ASYNC_DB_USERNAME"),
                environment.get("VRA_ASYNC_DB_PASSWORD"));
        if (!"vra_reconciliation_worker".equals(database.username())) {
            throw new IllegalStateException("RECONCILER requires the reconciliation worker identity");
        }
        String simulatorUrl = environment.get("VRA_SIMULATOR_URL");
        if (!"true".equals(environment.get("VRA_ASYNC_VALIDATION_MODE"))) {
            throw new IllegalArgumentException("RECONCILER simulator requires explicit validation mode");
        }
        URI simulator = simulatorUrl == null ? null : URI.create(simulatorUrl);
        if (simulator == null || !"http".equals(simulator.getScheme()) || simulator.getHost() == null) {
            throw new IllegalArgumentException("RECONCILER requires a simulator HTTP URL");
        }
        AsyncProperties tuning = new AsyncProperties();
        if (environment.containsKey("VRA_ASYNC_MAX_RECONCILIATION_CLAIMS_PER_CYCLE")) {
            throw new IllegalArgumentException("Reconciliation cycle limit is persisted by PostgreSQL");
        }
        duration(environment, "VRA_ASYNC_LEASE_DURATION", tuning::setLeaseDuration);
        duration(environment, "VRA_ASYNC_PROCESSING_TIMEOUT", tuning::setProcessingTimeout);
        duration(environment, "VRA_ASYNC_RETRY_BASE_DELAY", tuning::setRetryBaseDelay);
        duration(environment, "VRA_ASYNC_RETRY_MAX_DELAY", tuning::setRetryMaxDelay);
        duration(environment, "VRA_ASYNC_POLL_INTERVAL", tuning::setPollInterval);
        duration(environment, "VRA_ASYNC_RECONCILIATION_LEASE_DURATION", tuning::setReconciliationLeaseDuration);
        duration(environment, "VRA_ASYNC_RECONCILIATION_TIMEOUT", tuning::setReconciliationTimeout);
        duration(environment, "VRA_ASYNC_RECONCILIATION_BASE_DELAY", tuning::setReconciliationBaseDelay);
        duration(environment, "VRA_ASYNC_RECONCILIATION_MAX_DELAY", tuning::setReconciliationMaxDelay);
        number(environment, "VRA_ASYNC_RETRY_JITTER_FRACTION", value -> tuning.setRetryJitterFraction(Double.parseDouble(value)));
        number(environment, "VRA_ASYNC_BATCH_SIZE", value -> tuning.setBatchSize(Integer.parseInt(value)));
        number(environment, "VRA_ASYNC_MAX_IN_FLIGHT", value -> tuning.setMaxInFlight(Integer.parseInt(value)));
        AsyncStartupValidator.validate(tuning);
        DriverManagerDataSource source = new DriverManagerDataSource(
                database.url(), database.username(), database.password());
        Properties driverOptions = new Properties();
        driverOptions.setProperty("options", "-c statement_timeout="
                + tuning.getReconciliationTimeout().toMillis());
        source.setConnectionProperties(driverOptions);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT current_user")) {
            if (!row.next() || !"vra_reconciliation_worker".equals(row.getString(1))) {
                throw new IllegalStateException("RECONCILER database current_user mismatch");
            }
            try (ResultSet schema = statement.executeQuery("SELECT d.automatic_cycle,d.cycle_claim_count,"
                    + "d.cycle_claim_limit,c.state,c.external_knowledge,c.reason_code,c.next_eligible_at,"
                    + "c.claim_token,c.claim_until,c.lifetime_attempt_count,c.automatic_cycle,"
                    + "c.cycle_claim_count,c.cycle_claim_limit,h.observation "
                    + "FROM vra.outbox_delivery d JOIN vra.reconciliation_case c ON false "
                    + "JOIN vra.reconciliation_history h ON false LIMIT 0")) {
                // Preparing this read checks the committed V3 evidence surface under the real credential.
            }
            try (ResultSet capabilities = statement.executeQuery("SELECT "
                    + "COALESCE(has_function_privilege(current_user,"
                    + "to_regprocedure('vra.async_claim_reconciliation(varchar,integer,bigint)'),"
                    + "'EXECUTE'),false) AND "
                    + "COALESCE(has_function_privilege(current_user,"
                    + "to_regprocedure('vra.async_wait_reconciliation(uuid,uuid,varchar,bigint)'),"
                    + "'EXECUTE'),false) AND "
                    + "COALESCE(has_function_privilege(current_user,"
                    + "to_regprocedure('vra.async_confirm_external_success(uuid,uuid)'),"
                    + "'EXECUTE'),false) AND "
                    + "COALESCE(has_function_privilege(current_user,"
                    + "to_regprocedure('vra.async_confirm_external_no_effect(uuid,uuid,boolean,bigint)'),"
                    + "'EXECUTE'),false) AND "
                    + "COALESCE(has_function_privilege(current_user,"
                    + "to_regprocedure('vra.async_exhaust_reconciliation(uuid,uuid,varchar)'),"
                    + "'EXECUTE'),false)")) {
                if (!capabilities.next() || !capabilities.getBoolean(1)) {
                    throw new IllegalStateException("RECONCILER V3 capabilities are unavailable");
                }
            }
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("RECONCILER database identity check failed", error);
        }

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TransactionBoundary.class);
        context.registerBean(DataSource.class, () -> source);
        context.registerBean(JdbcClient.class, () -> JdbcClient.create(source));
        context.registerBean(PlatformTransactionManager.class, () -> new JdbcTransactionManager(source));
        AsyncDrainCoordinator admission = new AsyncDrainCoordinator(tuning.getMaxInFlight(),
                "reconciliation-attempt");
        context.registerBean("reconciliationJdbcPort", ReconciliationPort.class,
                () -> new JdbcReconciliationRepository(context.getBean(JdbcClient.class)));
        context.registerBean("reconciliationPort", ReconciliationPort.class,
                () -> new DrainGatedReconciliationPort(
                        context.getBean("reconciliationJdbcPort", ReconciliationPort.class), admission));
        context.registerBean(ExternalEffectPort.class,
                () -> new ValidationSimulatorHttpAdapter(simulator, tuning.getReconciliationTimeout()));
        context.registerBean(ReconciliationPolicy.class, () -> new ReconciliationPolicy(
                tuning.getReconciliationBaseDelay(), tuning.getReconciliationMaxDelay(),
                tuning.getRetryBaseDelay(), tuning.getRetryMaxDelay(), tuning.getRetryJitterFraction(),
                () -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0)));
        context.registerBean(ReconciliationService.class, () -> new ReconciliationService(
                context.getBean("reconciliationPort", ReconciliationPort.class),
                context.getBean(ExternalEffectPort.class),
                context.getBean(ReconciliationPolicy.class)));
        context.registerBean(ReconciliationLoop.class, () -> new ReconciliationLoop(
                context.getBean(ReconciliationService.class), tuning.getReconciliationLeaseDuration(),
                tuning.getPollInterval(), tuning.getBatchSize(),
                "reconciler-" + ProcessHandle.current().pid(), admission));
        try {
            context.refresh();
            return context;
        } catch (RuntimeException error) {
            context.close();
            throw error;
        }
    }

    public static void run(Map<String, String> environment) {
        try (AnnotationConfigApplicationContext context = start(environment)) {
            ReconciliationLoop loop = context.getBean(ReconciliationLoop.class);
            Thread hook = new Thread(loop::drain, "reconciliation-drain");
            Runtime.getRuntime().addShutdownHook(hook);
            try { loop.run(); }
            finally {
                loop.drain();
                try { Runtime.getRuntime().removeShutdownHook(hook); }
                catch (IllegalStateException shuttingDown) { /* The hook owns drain during JVM exit. */ }
            }
        }
    }

    private static void duration(Map<String, String> environment, String key, Consumer<Duration> setter) {
        number(environment, key, value -> setter.accept(DurationStyle.detectAndParse(value)));
    }

    private static void number(Map<String, String> environment, String key, Consumer<String> setter) {
        String value = environment.get(key);
        if (value != null) setter.accept(value);
    }

    /** Mechanical admission gate; all Stage-G policy stays in ReconciliationService. */
    private record DrainGatedReconciliationPort(ReconciliationPort delegate,
            AsyncDrainCoordinator admission) implements ReconciliationPort {
        @Override public List<Claim> claim(String workerRef, int batchSize, Duration lease) {
            return admission.claimIfAccepting(() -> delegate.claim(workerRef, batchSize, lease), List::of);
        }
        @Override public DeliveryCycle readDeliveryCycle(UUID eventId) {
            return delegate.readDeliveryCycle(eventId);
        }
        @Override public String waitIndeterminate(UUID caseId, UUID claimToken, Duration delay) {
            return delegate.waitIndeterminate(caseId, claimToken, delay);
        }
        @Override public boolean confirmSuccess(UUID caseId, UUID claimToken) {
            return delegate.confirmSuccess(caseId, claimToken);
        }
        @Override public String confirmNoEffect(UUID caseId, UUID claimToken,
                boolean retrySafe, Duration retryDelay) {
            return delegate.confirmNoEffect(caseId, claimToken, retrySafe, retryDelay);
        }
        @Override public boolean exhaust(UUID caseId, UUID claimToken) {
            return delegate.exhaust(caseId, claimToken);
        }
    }
}
