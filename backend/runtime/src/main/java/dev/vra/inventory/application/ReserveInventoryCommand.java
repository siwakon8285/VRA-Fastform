package dev.vra.inventory.application;

import java.util.Objects;

import dev.vra.inventory.domain.InventoryKey;

public record ReserveInventoryCommand(
        InventoryKey inventoryKey,
        long quantity
) {

    public ReserveInventoryCommand {
        Objects.requireNonNull(inventoryKey, "inventoryKey");
    }
}
