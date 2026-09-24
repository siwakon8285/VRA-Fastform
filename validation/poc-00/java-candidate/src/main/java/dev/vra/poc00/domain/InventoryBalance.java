package dev.vra.poc00.domain;

import java.util.Objects;

public record InventoryBalance(SkuId skuId, long onHand, long reserved, long version) {
    public InventoryBalance {
        Objects.requireNonNull(skuId, "skuId");
        if (onHand < 0 || reserved < 0 || reserved > onHand || version < 0)
            throw new IllegalArgumentException("Invalid inventory balance.");
    }
    public long available() { return onHand - reserved; }
    public static void requireQuantity(int quantity) {
        if (quantity <= 0) throw new DomainFailure.InvalidQuantity();
    }
    public InventoryBalance reserve(int quantity) {
        requireQuantity(quantity);
        if (quantity > available()) throw new DomainFailure.InsufficientStock();
        return new InventoryBalance(skuId, onHand, reserved + quantity, Math.incrementExact(version));
    }
}
