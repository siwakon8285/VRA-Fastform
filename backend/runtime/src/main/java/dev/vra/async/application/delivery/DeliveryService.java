package dev.vra.async.application.delivery;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;

/** Short transactions around guarded operations; effects run only after claim returns. */
@Service
public class DeliveryService {
    private final DeliveryPort port;

    public DeliveryService(DeliveryPort port) {
        this.port = Objects.requireNonNull(port);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<ClaimedDelivery> claim(TargetCode target, String workerRef, int batchSize, Duration lease) {
        return port.claim(target, workerRef, batchSize, lease);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean relinquish(ClaimedDelivery claim) {
        return port.relinquish(claim.eventId(), claim.claimToken());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean complete(ClaimedDelivery claim) {
        rejectReclaimedExternalOutcome(claim);
        return port.complete(claim.eventId(), claim.claimToken());
    }

    /** Explicit transient transition; retry classification and backoff belong to Stage E. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String retryTransient(ClaimedDelivery claim, Duration delay) {
        rejectReclaimedExternalOutcome(claim);
        return port.retry(claim.eventId(), claim.claimToken(), delay);
    }

    /** Explicit non-retryable transition; classification policy belongs to Stage E. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean failNonRetryable(ClaimedDelivery claim) {
        rejectReclaimedExternalOutcome(claim);
        return port.fail(claim.eventId(), claim.claimToken(), "NON_RETRYABLE", "NON_RETRYABLE");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UUID handoffReclaimedExternal(ClaimedDelivery claim) {
        if (!claim.requiresReconciliationBeforeExecute()) {
            throw new IllegalArgumentException("Only reclaimed external attempts use this handoff");
        }
        return port.handoffUnknown(claim.eventId(), claim.claimToken(), "RECLAIM_POSSIBLE_EXTERNAL_SEND");
    }

    /** Explicit UNKNOWN handoff; future first-send policy belongs to Stage F. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UUID handoffUnknown(ClaimedDelivery claim) {
        rejectReclaimedExternalOutcome(claim);
        return port.handoffUnknown(claim.eventId(), claim.claimToken(), "UNKNOWN_EXTERNAL_RESULT");
    }

    private static void rejectReclaimedExternalOutcome(ClaimedDelivery claim) {
        if (claim.requiresReconciliationBeforeExecute()) {
            throw new IllegalStateException("Reclaimed external attempt requires UNKNOWN handoff");
        }
    }
}
