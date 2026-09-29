package dev.vra.async.adapter.out.projection;

import java.util.Objects;
import java.util.UUID;

import dev.vra.async.application.projection.ReservationProjectionConsumer.ReservationProjection;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import dev.vra.inventory.domain.StockStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** First insert or equality check only; no projection maintenance capability. */
@Repository
public class JdbcReservationProjection implements ReservationProjection {
    private final JdbcClient jdbc;

    public JdbcReservationProjection(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void insertOrVerify(Payload payload) {
        Objects.requireNonNull(payload, "payload");
        boolean inserted = jdbc.sql("INSERT INTO vra.reservation_projection("
                        + "reservation_id,sku_id,owner_id,location_id,stock_status,quantity,projected_at) "
                        + "VALUES (:reservation,:sku,:owner,:location,:status,:quantity,statement_timestamp()) "
                        + "ON CONFLICT (reservation_id) DO NOTHING RETURNING reservation_id")
                .param("reservation", payload.reservationId())
                .param("sku", payload.skuId())
                .param("owner", payload.ownerId())
                .param("location", payload.locationId())
                .param("status", payload.stockStatus().name())
                .param("quantity", payload.quantity())
                .query(UUID.class).optional().isPresent();
        if (inserted) return;

        Payload existing = jdbc.sql("SELECT reservation_id,sku_id,owner_id,location_id,stock_status,quantity "
                        + "FROM vra.reservation_projection WHERE reservation_id=:reservation")
                .param("reservation", payload.reservationId())
                .query((rs, row) -> new Payload(
                        rs.getObject("reservation_id", UUID.class),
                        rs.getObject("sku_id", UUID.class),
                        rs.getObject("owner_id", UUID.class),
                        rs.getObject("location_id", UUID.class),
                        StockStatus.valueOf(rs.getString("stock_status")),
                        rs.getLong("quantity")))
                .optional().orElseThrow(() -> new IllegalStateException("Projection conflict row disappeared"));
        if (!existing.equals(payload)) {
            throw new IllegalStateException("Existing reservation projection differs from immutable event");
        }
    }
}
