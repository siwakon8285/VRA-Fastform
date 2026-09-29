package dev.vra.inventory.application;

import dev.vra.inventory.domain.InventoryKey;

public record IdempotentReserveInventoryCommand(
        String actorScope,
        String idempotencyKey,
        InventoryKey inventoryKey,
        long quantity
) {
}
