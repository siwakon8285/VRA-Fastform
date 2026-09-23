package dev.vra.poc00.domain;

import java.util.Objects;
import java.util.UUID;

/** Representative value only: this POC does not persist reservation identities. */
public record InventoryReservation(UUID id, SkuId skuId, int quantity, State state) {
    public enum State { ACTIVE, RELEASED }
    public InventoryReservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(state, "state");
        InventoryBalance.requireQuantity(quantity);
    }
}
