package dev.vra.inventory.domain;

import java.util.Objects;
import java.util.UUID;

public record InventoryKey(
        UUID skuId,
        UUID ownerId,
        UUID locationId,
        StockStatus stockStatus
) {

    public InventoryKey {
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(locationId, "locationId");
        Objects.requireNonNull(stockStatus, "stockStatus");
    }
}
