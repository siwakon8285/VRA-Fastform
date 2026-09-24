package dev.vra.poc00.infrastructure;

import dev.vra.poc00.application.InventoryRepository;
import dev.vra.poc00.domain.InventoryBalance;
import dev.vra.poc00.domain.SkuId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInventoryRepository implements InventoryRepository {
    private final JdbcClient jdbc;
    public JdbcInventoryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<InventoryBalance> reserve(SkuId skuId, int quantity) {
        InventoryBalance.requireQuantity(quantity);
        return jdbc.sql("""
                UPDATE inventory_balance SET reserved = reserved + :quantity, version = version + 1
                WHERE sku_id = :sku AND on_hand - reserved >= :quantity
                RETURNING sku_id, on_hand, reserved, version
                """).param("quantity", quantity).param("sku", skuId.value())
                .query((rs, row) -> new InventoryBalance(new SkuId(rs.getObject("sku_id", UUID.class)),
                        rs.getLong("on_hand"), rs.getLong("reserved"), rs.getLong("version")))
                .optional();
    }

    @Override
    public boolean exists(SkuId skuId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM inventory_balance WHERE sku_id = :sku)")
                .param("sku", skuId.value()).query(Boolean.class).single();
    }
}
