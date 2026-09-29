package dev.vra.inventory.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InventoryReservation(
        UUID reservationId,
        InventoryKey inventoryKey,
        long quantity,
        Instant createdAt
) {

    public InventoryReservation {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(inventoryKey, "inventoryKey");
        Objects.requireNonNull(createdAt, "createdAt");

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }
}
