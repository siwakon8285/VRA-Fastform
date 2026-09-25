package dev.vra.inventory.application;

import java.util.Objects;
import java.util.UUID;

public sealed interface IdempotentReservationResult
        permits IdempotentReservationResult.Succeeded,
                IdempotentReservationResult.Rejected,
                IdempotentReservationResult.Conflict {

    record Succeeded(UUID reservationId, long inventoryVersion)
            implements IdempotentReservationResult {
        public Succeeded {
            Objects.requireNonNull(reservationId, "reservationId");
        }
    }

    record Rejected(ReservationFailureCode code)
            implements IdempotentReservationResult {
        public Rejected {
            Objects.requireNonNull(code, "code");
        }
    }

    record Conflict(IdempotencyFailureCode code)
            implements IdempotentReservationResult {
        public Conflict {
            Objects.requireNonNull(code, "code");
        }
    }

    enum IdempotencyFailureCode {
        IDEMPOTENCY_KEY_REUSED
    }
}
