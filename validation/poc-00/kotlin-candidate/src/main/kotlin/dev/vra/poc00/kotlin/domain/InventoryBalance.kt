package dev.vra.poc00.kotlin.domain

data class InventoryBalance(
    val skuId: SkuId,
    val onHand: Long,
    val reserved: Long,
    val version: Long
) {
    init {
        require(onHand >= 0 && reserved >= 0 && reserved <= onHand && version >= 0) {
            "Invalid inventory balance."
        }
    }

    val available: Long get() = onHand - reserved

    fun reserve(quantity: Int): InventoryBalance {
        requireQuantity(quantity)
        if (quantity.toLong() > available) throw DomainFailure.InsufficientStock()
        return copy(reserved = reserved + quantity, version = Math.incrementExact(version))
    }

    companion object {
        fun requireQuantity(quantity: Int) {
            if (quantity <= 0) throw DomainFailure.InvalidQuantity()
        }
    }
}
