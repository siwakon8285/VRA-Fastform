package dev.vra.async.bootstrap;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import dev.vra.async.application.delivery.DeliveryFailureClassifier.Classification;
import dev.vra.async.application.delivery.DeliveryFailureClassifier.FailureClass;
import dev.vra.async.application.delivery.DeliveryPort;
import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.application.delivery.DeliveryRetryPolicy;
import dev.vra.async.application.delivery.DeliveryService;
import dev.vra.async.application.external.ExternalEffectPort;
import dev.vra.async.application.projection.ReservationProjectionConsumer;
import dev.vra.async.contract.ReservationCreatedEventV1;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import dev.vra.inventory.domain.StockStatus;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Bounded normal delivery dispatch; every durable claim commits before its effect. */
public final class OutboxWorkerLoop implements Runnable {
    private final DeliveryService delivery;
    private final DeliveryPort port;
    private final JdbcClient jdbc;
    private final ReservationProjectionConsumer projection;
    private final ExternalEffectPort external;
    private final DeliveryRetryPolicy retry;
    private final TargetCode target;
    private final Duration lease;
    private final Duration pollInterval;
    private final Duration drainGrace;
    private final int batchSize;
    private final String workerRef;
    private final AsyncDrainCoordinator drain;

    public OutboxWorkerLoop(DeliveryService delivery, DeliveryPort port, JdbcClient jdbc,
            ReservationProjectionConsumer projection, ExternalEffectPort external,
            DeliveryRetryPolicy retry, TargetCode target, AsyncProperties tuning, String workerRef) {
        this.delivery = Objects.requireNonNull(delivery);
        this.port = Objects.requireNonNull(port);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.projection = projection;
        this.external = external;
        this.retry = Objects.requireNonNull(retry);
        this.target = Objects.requireNonNull(target);
        this.lease = tuning.getLeaseDuration();
        this.pollInterval = tuning.getPollInterval();
        this.drainGrace = tuning.getLeaseDuration();
        this.batchSize = tuning.getBatchSize();
        this.workerRef = Objects.requireNonNull(workerRef);
        if ((target == TargetCode.RESERVATION_PROJECTION && projection == null)
                || (target == TargetCode.VALIDATION_EXTERNAL_EFFECT && external == null)) {
            throw new IllegalArgumentException("Selected target adapter is unavailable");
        }
        this.drain = new AsyncDrainCoordinator(tuning.getMaxInFlight(), "outbox-attempt");
    }

    /** Reserves every possible claim slot before the committed V3 claim call. */
    public int pollOnce() {
        synchronized (drain.admission()) {
            if (!drain.accepting()) return 0;
            int reserved = 0;
            while (reserved < batchSize && drain.reserve()) reserved++;
            if (reserved == 0) return 0;
            List<ClaimedDelivery> claims;
            try {
                claims = delivery.claim(target, workerRef, reserved, lease);
            } catch (RuntimeException failure) {
                for (int i = 0; i < reserved; i++) drain.release();
                throw failure;
            }
            for (int i = claims.size(); i < reserved; i++) drain.release();
            for (ClaimedDelivery claim : claims) {
                try {
                    drain.submit(() -> process(claim));
                } catch (RuntimeException rejected) {
                    drain.release();
                    // No effect began. Preserve the guarded claim if relinquishment is unavailable.
                    try { delivery.relinquish(claim); } catch (RuntimeException ignored) { }
                    throw rejected;
                }
            }
            return claims.size();
        }
    }

    private void process(ClaimedDelivery claim) {
        try {
            if (claim.requiresReconciliationBeforeExecute()) {
                if (!drain.beginWork()) return;
                delivery.handoffReclaimedExternal(claim);
                return;
            }
            ReservationCreatedEventV1 event;
            try {
                event = readEvent(claim.eventId());
            } catch (IllegalArgumentException unsupported) {
                if (!drain.beginWork()) return;
                port.fail(claim.eventId(), claim.claimToken(), "POISON", "UNSUPPORTED_EVENT_CONTRACT");
                return;
            }
            if (!drain.beginWork()) return;
            if (claim.targetCode() == TargetCode.RESERVATION_PROJECTION) {
                consumeProjection(claim, event);
            } else {
                executeExternal(claim, event);
            }
        } catch (RuntimeException uncertain) {
            // An interrupted/ambiguous attempt is left for PostgreSQL lease recovery.
            System.err.println("Outbox attempt retained for guarded recovery: "
                    + uncertain.getClass().getSimpleName());
        } finally {
            // This runs only after this attempt's synchronous effect and finalizer have stopped.
            // V3 denies a completed, replaced, or already relinquished token.
            if (!drain.accepting()) relinquishStopped(claim);
        }
    }

    private void consumeProjection(ClaimedDelivery claim, ReservationCreatedEventV1 event) {
        try {
            projection.consume(event);
            delivery.complete(claim);
        } catch (TransientDataAccessException rolledBack) {
            if (Thread.currentThread().isInterrupted()) return;
            Classification evidence = new Classification(FailureClass.RETRYABLE_TRANSIENT, "RETRYABLE_TRANSIENT");
            Duration delay = retry.nextDelay(claim, evidence).orElse(Duration.ofMillis(1));
            delivery.retryTransient(claim, delay);
        }
    }

    private void executeExternal(ClaimedDelivery claim, ReservationCreatedEventV1 event) {
        ExternalEffectPort.ExecuteOutcome outcome;
        try {
            outcome = external.execute(claim.eventId(), event.payload());
        } catch (RuntimeException uncertain) {
            outcome = ExternalEffectPort.ExecuteOutcome.UNKNOWN_OUTCOME;
        }
        switch (outcome) {
            case CONFIRMED_SUCCEEDED -> delivery.complete(claim);
            case UNKNOWN_OUTCOME -> delivery.handoffUnknown(claim);
            case CONFLICT -> delivery.failNonRetryable(claim);
        }
    }

    private ReservationCreatedEventV1 readEvent(UUID eventId) {
        return jdbc.sql("SELECT event_id,event_type,schema_version,occurred_at,reservation_id,"
                        + "sku_id,owner_id,location_id,stock_status,quantity "
                        + "FROM vra.outbox_event WHERE event_id=:event")
                .param("event", eventId)
                .query((row, number) -> new ReservationCreatedEventV1(
                        row.getObject("event_id", UUID.class), row.getString("event_type"),
                        row.getInt("schema_version"), row.getTimestamp("occurred_at").toInstant(),
                        new Payload(row.getObject("reservation_id", UUID.class),
                                row.getObject("sku_id", UUID.class), row.getObject("owner_id", UUID.class),
                                row.getObject("location_id", UUID.class),
                                StockStatus.valueOf(row.getString("stock_status")), row.getLong("quantity"))))
                .single();
    }

    private void relinquishStopped(ClaimedDelivery claim) {
        try { delivery.relinquish(claim); }
        catch (RuntimeException unavailable) {
            // The durable PROCESSING claim remains eligible at lease expiry.
        }
    }

    public void drain() { drain.drain(drainGrace); }

    @Override public void run() {
        while (drain.accepting() && !Thread.currentThread().isInterrupted()) {
            try { pollOnce(); }
            catch (RuntimeException failure) {
                System.err.println("Outbox poll retained for PostgreSQL rediscovery: "
                        + failure.getClass().getSimpleName());
            }
            LockSupport.parkNanos(pollInterval.toNanos());
        }
    }
}
