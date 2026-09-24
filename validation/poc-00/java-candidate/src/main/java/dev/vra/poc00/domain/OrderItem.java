package dev.vra.poc00.domain;

import java.util.Objects;

public record OrderItem(SkuId skuId, String productNameSnapshot, int quantity, Money unitPrice) {
    public OrderItem {
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(productNameSnapshot, "productNameSnapshot");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (productNameSnapshot.isBlank()) throw new IllegalArgumentException("Product name is required.");
        InventoryBalance.requireQuantity(quantity);
    }
}
