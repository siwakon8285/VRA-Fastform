package dev.vra.poc00.kotlin.domain

import java.util.UUID

data class InventoryReservation(
    val id: UUID,
    val skuId: SkuId,
    val quantity: Int,
    val state: State
) {
    enum class State { ACTIVE, RELEASED }

    init {
        InventoryBalance.requireQuantity(quantity)
    }
}
