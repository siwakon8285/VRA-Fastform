package dev.vra.inventory.application;

import java.util.Objects;
import java.util.UUID;

public sealed interface ReservationDecision
        permits ReservationDecision.Succeeded, ReservationDecision.Rejected {

    record Succeeded(UUID reservationId, long inventoryVersion)
            implements ReservationDecision {

        public Succeeded {
            Objects.requireNonNull(reservationId, "reservationId");
        }
    }

    record Rejected(ReservationFailureCode code)
            implements ReservationDecision {

        public Rejected {
            Objects.requireNonNull(code, "code");
        }
    }
}
