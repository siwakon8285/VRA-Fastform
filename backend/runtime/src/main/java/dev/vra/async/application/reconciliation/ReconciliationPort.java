package dev.vra.async.application.reconciliation;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Operation-shaped boundary to the committed V3 reconciliation capabilities. */
public interface ReconciliationPort {
    record Claim(UUID caseId, UUID eventId, UUID claimToken, long lifetimeAttemptCount,
                 int automaticCycle, int cycleClaimCount, int cycleClaimLimit) {
        public Claim {
            if (caseId == null || eventId == null || claimToken == null || lifetimeAttemptCount < 1
                    || automaticCycle < 1 || cycleClaimCount < 1 || cycleClaimLimit < 1
                    || cycleClaimCount > cycleClaimLimit) {
                throw new IllegalArgumentException("Invalid query-bearing reconciliation claim");
            }
        }
    }

    record DeliveryCycle(int automaticCycle, int cycleClaimCount, int cycleClaimLimit) {
        public DeliveryCycle {
            if (automaticCycle < 1 || cycleClaimCount < 1 || cycleClaimLimit < 1
                    || cycleClaimCount > cycleClaimLimit + 1) {
                throw new IllegalArgumentException("Invalid persisted delivery cycle");
            }
        }
    }

    List<Claim> claim(String workerRef, int batchSize, Duration lease);

    DeliveryCycle readDeliveryCycle(UUID eventId);

    String waitIndeterminate(UUID caseId, UUID claimToken, Duration delay);

    boolean confirmSuccess(UUID caseId, UUID claimToken);

    String confirmNoEffect(UUID caseId, UUID claimToken, boolean retrySafe, Duration retryDelay);

    boolean exhaust(UUID caseId, UUID claimToken);
}
