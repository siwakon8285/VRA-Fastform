package dev.vra.inventory.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "vra", name = "inventory_reservation")
public class ReservationEntity {

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "stock_status", nullable = false, length = 32)
    private String stockStatus;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReservationEntity() {
    }

    ReservationEntity(
            UUID reservationId,
            UUID skuId,
            UUID ownerId,
            UUID locationId,
            String stockStatus,
            long quantity,
            Instant createdAt
    ) {
        this.reservationId = reservationId;
        this.skuId = skuId;
        this.ownerId = ownerId;
        this.locationId = locationId;
        this.stockStatus = stockStatus;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }
}
