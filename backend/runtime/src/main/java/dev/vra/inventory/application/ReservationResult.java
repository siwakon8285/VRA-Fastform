package dev.vra.inventory.application;

import java.util.UUID;

public record ReservationResult(
        UUID reservationId,
        long inventoryVersion
) {
}
