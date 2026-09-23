package dev.vra.poc00.kotlin.application

import dev.vra.poc00.kotlin.domain.InventoryBalance
import dev.vra.poc00.kotlin.domain.SkuId

interface InventoryRepository {
    fun reserve(skuId: SkuId, quantity: Int): InventoryBalance?
    fun exists(skuId: SkuId): Boolean
}
