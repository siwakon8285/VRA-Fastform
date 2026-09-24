package dev.vra.poc00.application;

import dev.vra.poc00.domain.InventoryBalance;
import dev.vra.poc00.domain.SkuId;
import java.util.Optional;

public interface InventoryRepository {
    Optional<InventoryBalance> reserve(SkuId skuId, int quantity);
    boolean exists(SkuId skuId);
}
