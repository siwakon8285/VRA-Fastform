package dev.vra.poc00.kotlin.application

import dev.vra.poc00.kotlin.domain.DomainFailure
import dev.vra.poc00.kotlin.domain.InventoryBalance
import dev.vra.poc00.kotlin.domain.SkuId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReservationApplicationService(private val inventory: InventoryRepository) {
    /** Public proxied application boundary; only local SQL work occurs in this transaction. */
    @Transactional
    fun reserve(skuId: SkuId, quantity: Int): InventoryBalance {
        InventoryBalance.requireQuantity(quantity)
        val result = inventory.reserve(skuId, quantity)
        if (result != null) return result
        if (!inventory.exists(skuId)) throw DomainFailure.UnknownSku()
        throw DomainFailure.InsufficientStock()
    }
}
