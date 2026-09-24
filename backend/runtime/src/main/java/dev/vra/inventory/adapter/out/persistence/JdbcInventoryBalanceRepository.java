package dev.vra.inventory.adapter.out.persistence;

import java.util.Optional;

import dev.vra.inventory.application.port.InventoryBalanceRepository;
import dev.vra.inventory.domain.InventoryKey;
import dev.vra.inventory.domain.StockStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInventoryBalanceRepository
        implements InventoryBalanceRepository {

    private final JdbcClient jdbcClient;

    public JdbcInventoryBalanceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ReserveAttempt reserve(
            InventoryKey inventoryKey,
            long quantity,
            long expectedVersion
    ) {
        Optional<Long> updatedVersion = jdbcClient.sql("""
                UPDATE vra.inventory_balance
                SET reserved = reserved + :quantity,
                    version = version + 1
                WHERE sku_id = :skuId
                  AND owner_id = :ownerId
                  AND location_id = :locationId
                  AND stock_status = :stockStatus
                  AND stock_status = 'AVAILABLE'
                  AND version = :expectedVersion
                  AND on_hand - reserved >= :quantity
                RETURNING version
                """)
                .param("quantity", quantity)
                .param("skuId", inventoryKey.skuId())
                .param("ownerId", inventoryKey.ownerId())
                .param("locationId", inventoryKey.locationId())
                .param("stockStatus", inventoryKey.stockStatus().name())
                .param("expectedVersion", expectedVersion)
                .query(Long.class)
                .optional();

        if (updatedVersion.isPresent()) {
            return ReserveAttempt.reserved(updatedVersion.orElseThrow());
        }

        return classifyRejection(
                inventoryKey,
                quantity,
                expectedVersion
        );
    }

    private ReserveAttempt classifyRejection(
            InventoryKey inventoryKey,
            long quantity,
            long expectedVersion
    ) {
        Optional<BalanceSnapshot> snapshot = jdbcClient.sql("""
                SELECT on_hand,
                       reserved,
                       version,
                       stock_status
                FROM vra.inventory_balance
                WHERE sku_id = :skuId
                  AND owner_id = :ownerId
                  AND location_id = :locationId
                  AND stock_status = :stockStatus
                """)
                .param("skuId", inventoryKey.skuId())
                .param("ownerId", inventoryKey.ownerId())
                .param("locationId", inventoryKey.locationId())
                .param("stockStatus", inventoryKey.stockStatus().name())
                .query((resultSet, rowNumber) -> new BalanceSnapshot(
                        resultSet.getLong("on_hand"),
                        resultSet.getLong("reserved"),
                        resultSet.getLong("version"),
                        StockStatus.valueOf(resultSet.getString("stock_status"))
                ))
                .optional();

        if (snapshot.isEmpty()) {
            return ReserveAttempt.rejected(ReserveStatus.NOT_FOUND);
        }

        BalanceSnapshot balance = snapshot.orElseThrow();

        if (!balance.stockStatus().isReservable()) {
            return ReserveAttempt.rejected(ReserveStatus.NOT_RESERVABLE);
        }

        if (balance.version() != expectedVersion) {
            return ReserveAttempt.rejected(ReserveStatus.VERSION_CONFLICT);
        }

        if (balance.onHand() - balance.reserved() < quantity) {
            return ReserveAttempt.rejected(ReserveStatus.INSUFFICIENT_STOCK);
        }

        // A concurrent change can make the write miss even if the follow-up
        // snapshot no longer explains the original failure. POC-02 owns
        // contention semantics; surface this conservatively as a version conflict.
        return ReserveAttempt.rejected(ReserveStatus.VERSION_CONFLICT);
    }

    private record BalanceSnapshot(
            long onHand,
            long reserved,
            long version,
            StockStatus stockStatus
    ) {
    }
}
