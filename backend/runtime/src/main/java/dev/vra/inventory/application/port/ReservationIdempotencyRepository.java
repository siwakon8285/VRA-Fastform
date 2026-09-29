package dev.vra.inventory.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationRequestFingerprint.Fingerprint;

/** Operations join a caller-owned READ COMMITTED transaction. */
public interface ReservationIdempotencyRepository {
    ClaimResult claimOrResolve(String actorScope, String idempotencyKey,
                               Fingerprint fingerprint, Instant createdAt) throws IllegalStateException;

    void completeSucceeded(String actorScope, String idempotencyKey,
                           Fingerprint fingerprint, UUID reservationId,
                           long inventoryVersion, Instant completedAt) throws IllegalStateException;

    void completeRejected(String actorScope, String idempotencyKey,
                          Fingerprint fingerprint, ReservationFailureCode rejectionCode,
                          Instant completedAt) throws IllegalStateException;

    sealed interface ClaimResult {
        record Claimed() implements ClaimResult {}

        record ReplayedSuccess(UUID reservationId, long inventoryVersion) implements ClaimResult {
            public ReplayedSuccess {
                Objects.requireNonNull(reservationId, "reservationId");
            }
        }

        record ReplayedRejection(ReservationFailureCode code) implements ClaimResult {
            public ReplayedRejection {
                Objects.requireNonNull(code, "code");
            }
        }

        record FingerprintConflict() implements ClaimResult {}
    }
}
