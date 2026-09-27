package dev.vra.async.bootstrap;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import javax.sql.DataSource;

import dev.vra.async.adapter.out.delivery.JdbcDeliveryRepository;
import dev.vra.async.adapter.out.external.ValidationSimulatorHttpAdapter;
import dev.vra.async.adapter.out.projection.JdbcConsumerInbox;
import dev.vra.async.adapter.out.projection.JdbcReservationProjection;
import dev.vra.async.application.delivery.DeliveryPort;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.application.delivery.DeliveryRetryPolicy;
import dev.vra.async.application.delivery.DeliveryService;
import dev.vra.async.application.external.ExternalEffectPort;
import dev.vra.async.application.projection.ReservationProjectionConsumer;
import dev.vra.async.application.projection.ReservationProjectionConsumer.ConsumerInbox;
import dev.vra.async.application.projection.ReservationProjectionConsumer.ReservationProjection;
import dev.vra.platform.configuration.RuntimeDatabaseProperties;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Isolated non-web worker composition under its effective PostgreSQL identity. */
public final class OutboxWorkerMode {
    private OutboxWorkerMode() {}

    @EnableTransactionManagement
    static class TransactionBoundary {}

    public static AnnotationConfigApplicationContext start(Map<String, String> environment) {
        Objects.requireNonNull(environment);
        RuntimeDatabaseProperties database = new RuntimeDatabaseProperties(
                environment.get("VRA_ASYNC_DB_URL"), environment.get("VRA_ASYNC_DB_USERNAME"),
                environment.get("VRA_ASYNC_DB_PASSWORD"));
        if (!"vra_outbox_worker".equals(database.username())) {
            throw new IllegalStateException("OUTBOX_WORKER requires the outbox worker identity");
        }
        if (environment.containsKey("VRA_ASYNC_MAX_DELIVERY_CLAIMS_PER_CYCLE")
                || environment.containsKey("VRA_ASYNC_MAX_RECONCILIATION_CLAIMS_PER_CYCLE")) {
            throw new IllegalArgumentException("Claim cycle limits are persisted by PostgreSQL");
        }
        String selected = environment.getOrDefault("VRA_ASYNC_TARGET_CODE", "RESERVATION_PROJECTION");
        TargetCode target;
        try { target = TargetCode.valueOf(selected); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown worker target", invalid); }
        URI simulator = null;
        if (target == TargetCode.VALIDATION_EXTERNAL_EFFECT) {
            if (!"true".equals(environment.get("VRA_ASYNC_VALIDATION_MODE"))) {
                throw new IllegalArgumentException("External validation target requires explicit validation mode");
            }
            String value = environment.get("VRA_SIMULATOR_URL");
            simulator = value == null ? null : URI.create(value);
            if (simulator == null || !"http".equals(simulator.getScheme()) || simulator.getHost() == null) {
                throw new IllegalArgumentException("External validation target requires simulator URL");
            }
        }
        AsyncProperties tuning = new AsyncProperties();
        duration(environment, "VRA_ASYNC_LEASE_DURATION", tuning::setLeaseDuration);
        duration(environment, "VRA_ASYNC_PROCESSING_TIMEOUT", tuning::setProcessingTimeout);
        duration(environment, "VRA_ASYNC_RETRY_BASE_DELAY", tuning::setRetryBaseDelay);
        duration(environment, "VRA_ASYNC_RETRY_MAX_DELAY", tuning::setRetryMaxDelay);
        duration(environment, "VRA_ASYNC_POLL_INTERVAL", tuning::setPollInterval);
        duration(environment, "VRA_ASYNC_RECONCILIATION_LEASE_DURATION", tuning::setReconciliationLeaseDuration);
        duration(environment, "VRA_ASYNC_RECONCILIATION_TIMEOUT", tuning::setReconciliationTimeout);
        duration(environment, "VRA_ASYNC_RECONCILIATION_BASE_DELAY", tuning::setReconciliationBaseDelay);
        duration(environment, "VRA_ASYNC_RECONCILIATION_MAX_DELAY", tuning::setReconciliationMaxDelay);
        number(environment, "VRA_ASYNC_RETRY_JITTER_FRACTION", v -> tuning.setRetryJitterFraction(Double.parseDouble(v)));
        number(environment, "VRA_ASYNC_BATCH_SIZE", v -> tuning.setBatchSize(Integer.parseInt(v)));
        number(environment, "VRA_ASYNC_MAX_IN_FLIGHT", v -> tuning.setMaxInFlight(Integer.parseInt(v)));
        AsyncStartupValidator.validate(tuning);

        DriverManagerDataSource source = new DriverManagerDataSource(
                database.url(), database.username(), database.password());
        Properties driverOptions = new Properties();
        driverOptions.setProperty("options", "-c statement_timeout=" + tuning.getProcessingTimeout().toMillis());
        source.setConnectionProperties(driverOptions);
        validateIdentityAndSchema(source);
        final URI selectedSimulator = simulator;
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TransactionBoundary.class);
        context.registerBean(DataSource.class, () -> source);
        context.registerBean(JdbcClient.class, () -> JdbcClient.create(source));
        context.registerBean(PlatformTransactionManager.class, () -> new JdbcTransactionManager(source));
        context.registerBean(DeliveryPort.class, () -> new JdbcDeliveryRepository(context.getBean(JdbcClient.class)));
        context.registerBean(DeliveryService.class, () -> new DeliveryService(context.getBean(DeliveryPort.class)));
        if (target == TargetCode.RESERVATION_PROJECTION) {
            context.registerBean(ConsumerInbox.class,
                    () -> new JdbcConsumerInbox(context.getBean(JdbcClient.class)));
            context.registerBean(ReservationProjection.class,
                    () -> new JdbcReservationProjection(context.getBean(JdbcClient.class)));
            context.registerBean(ReservationProjectionConsumer.class,
                    () -> new ReservationProjectionConsumer(context.getBean(ConsumerInbox.class),
                            context.getBean(ReservationProjection.class)));
        } else {
            context.registerBean(ExternalEffectPort.class,
                    () -> new ValidationSimulatorHttpAdapter(selectedSimulator, tuning.getProcessingTimeout()));
        }
        context.registerBean(DeliveryRetryPolicy.class, () -> new DeliveryRetryPolicy(
                tuning.getRetryBaseDelay(), tuning.getRetryMaxDelay(), tuning.getRetryJitterFraction(),
                () -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0)));
        context.registerBean(OutboxWorkerLoop.class, () -> new OutboxWorkerLoop(
                context.getBean(DeliveryService.class), context.getBean(DeliveryPort.class),
                context.getBean(JdbcClient.class), target == TargetCode.RESERVATION_PROJECTION
                        ? context.getBean(ReservationProjectionConsumer.class) : null,
                target == TargetCode.VALIDATION_EXTERNAL_EFFECT ? context.getBean(ExternalEffectPort.class) : null,
                context.getBean(DeliveryRetryPolicy.class), target, tuning,
                "outbox-" + ProcessHandle.current().pid()));
        try { context.refresh(); return context; }
        catch (RuntimeException failure) { context.close(); throw failure; }
    }

    public static void run(Map<String, String> environment) {
        try (AnnotationConfigApplicationContext context = start(environment)) {
            OutboxWorkerLoop loop = context.getBean(OutboxWorkerLoop.class);
            Thread hook = new Thread(loop::drain, "outbox-drain");
            Runtime.getRuntime().addShutdownHook(hook);
            try { loop.run(); }
            finally {
                loop.drain();
                try { Runtime.getRuntime().removeShutdownHook(hook); }
                catch (IllegalStateException shuttingDown) { /* The hook owns drain during JVM exit. */ }
            }
        }
    }

    private static void validateIdentityAndSchema(DataSource source) {
        try (Connection connection = source.getConnection(); Statement sql = connection.createStatement();
             ResultSet user = sql.executeQuery("SELECT current_user")) {
            if (!user.next() || !"vra_outbox_worker".equals(user.getString(1))) {
                throw new IllegalStateException("OUTBOX_WORKER database current_user mismatch");
            }
            try (ResultSet schema = sql.executeQuery("SELECT e.event_id,e.event_type,e.schema_version,"
                    + "e.occurred_at,e.reservation_id,e.sku_id,e.owner_id,e.location_id,"
                    + "e.stock_status,e.quantity,d.cycle_claim_limit "
                    + "FROM vra.outbox_event e JOIN vra.outbox_delivery d ON false LIMIT 0")) {
                // Preparing a read proves the selected V3 evidence shape under this identity.
            }
            try (ResultSet capabilities = sql.executeQuery("SELECT "
                    + "COALESCE(has_function_privilege(current_user,"
                    + "to_regprocedure('vra.async_claim_delivery(varchar,varchar,integer,bigint)'),"
                    + "'EXECUTE'),false) AND "
                    + "COALESCE(has_function_privilege(current_user,"
                    + "to_regprocedure('vra.async_relinquish_delivery(uuid,uuid)'),"
                    + "'EXECUTE'),false) AND "
                    + "COALESCE(has_function_privilege(current_user,"
                    + "to_regprocedure('vra.async_handoff_unknown(uuid,uuid,varchar)'),"
                    + "'EXECUTE'),false)")) {
                if (!capabilities.next() || !capabilities.getBoolean(1)) {
                    throw new IllegalStateException("OUTBOX_WORKER V3 capabilities are unavailable");
                }
            }
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("OUTBOX_WORKER database identity check failed", error);
        }
    }

    private static void duration(Map<String, String> environment, String key, Consumer<Duration> setter) {
        number(environment, key, value -> setter.accept(DurationStyle.detectAndParse(value)));
    }
    private static void number(Map<String, String> environment, String key, Consumer<String> setter) {
        String value = environment.get(key);
        if (value != null) setter.accept(value);
    }
}
