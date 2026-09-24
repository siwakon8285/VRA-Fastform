package dev.vra.poc00.kotlin.infrastructure

import dev.vra.poc00.kotlin.application.InventoryRepository
import dev.vra.poc00.kotlin.domain.InventoryBalance
import dev.vra.poc00.kotlin.domain.SkuId
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JdbcInventoryRepository(private val jdbc: JdbcClient) : InventoryRepository {
    override fun reserve(skuId: SkuId, quantity: Int): InventoryBalance? {
        InventoryBalance.requireQuantity(quantity)
        return jdbc.sql(
            """
            UPDATE inventory_balance SET reserved = reserved + :quantity, version = version + 1
            WHERE sku_id = :sku AND on_hand - reserved >= :quantity
            RETURNING sku_id, on_hand, reserved, version
            """.trimIndent()
        ).param("quantity", quantity)
            .param("sku", skuId.value)
            .query { rs, _ ->
                InventoryBalance(
                    SkuId(rs.getObject("sku_id", UUID::class.java)),
                    rs.getLong("on_hand"),
                    rs.getLong("reserved"),
                    rs.getLong("version")
                )
            }
            .optional()
            .orElse(null)
    }

    override fun exists(skuId: SkuId): Boolean =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM inventory_balance WHERE sku_id = :sku)")
            .param("sku", skuId.value)
            .query(Boolean::class.javaObjectType)
            .single()
}
