package dev.vra.poc00.application;

import dev.vra.poc00.domain.DomainFailure;
import dev.vra.poc00.domain.InventoryBalance;
import dev.vra.poc00.domain.SkuId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationApplicationService {
    private final InventoryRepository inventory;
    public ReservationApplicationService(InventoryRepository inventory) { this.inventory = inventory; }

    /** The transaction begins at this public application boundary through Spring's proxy. */
    @Transactional
    public InventoryBalance reserve(SkuId skuId, int quantity) {
        InventoryBalance.requireQuantity(quantity);
        return inventory.reserve(skuId, quantity).orElseThrow(() -> {
            if (!inventory.exists(skuId)) return new DomainFailure.UnknownSku();
            return new DomainFailure.InsufficientStock();
        });
    }
}
