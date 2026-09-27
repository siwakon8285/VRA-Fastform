package dev.vra.async.application.delivery;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** The guarded, operation-shaped delivery boundary. */
public interface DeliveryPort {
    enum TargetCode { RESERVATION_PROJECTION, VALIDATION_EXTERNAL_EFFECT }

    record ClaimedDelivery(UUID eventId, UUID claimToken, TargetCode targetCode,
                           boolean reclaimed, long deliveryAttemptCount, int automaticCycle,
                           int cycleClaimCount, int cycleClaimLimit) {
        public ClaimedDelivery {
            if (eventId == null || claimToken == null || targetCode == null
                    || deliveryAttemptCount < 1 || automaticCycle < 1 || cycleClaimCount < 1
                    || cycleClaimLimit < 1 || cycleClaimCount > cycleClaimLimit) {
                throw new IllegalArgumentException("Invalid dispatchable delivery claim");
            }
        }

        /** A reclaimed external attempt must be handed to UNKNOWN before any execute. */
        public boolean requiresReconciliationBeforeExecute() {
            return reclaimed && targetCode == TargetCode.VALIDATION_EXTERNAL_EFFECT;
        }
    }

    List<ClaimedDelivery> claim(TargetCode target, String workerRef, int batchSize, Duration lease);

    boolean relinquish(UUID eventId, UUID claimToken);

    boolean complete(UUID eventId, UUID claimToken);

    String retry(UUID eventId, UUID claimToken, Duration delay);

    boolean fail(UUID eventId, UUID claimToken, String failureClass, String reasonCode);

    UUID handoffUnknown(UUID eventId, UUID claimToken, String reasonCode);
}
