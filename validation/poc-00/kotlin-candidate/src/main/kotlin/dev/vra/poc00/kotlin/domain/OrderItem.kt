package dev.vra.poc00.kotlin.domain

data class OrderItem(
    val skuId: SkuId,
    val productNameSnapshot: String,
    val quantity: Int,
    val unitPrice: Money
) {
    init {
        require(productNameSnapshot.isNotBlank()) { "Product name is required." }
        InventoryBalance.requireQuantity(quantity)
    }
}
