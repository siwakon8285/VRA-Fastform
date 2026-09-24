package dev.vra.inventory.application;

import java.util.Objects;
import java.util.UUID;

import dev.vra.inventory.domain.InventoryKey;

public record ReserveInventoryCommand(
        UUID reservationId,
        InventoryKey inventoryKey,
        long quantity,
        long expectedVersion
) {

    public ReserveInventoryCommand {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(inventoryKey, "inventoryKey");
    }
}
