package dev.vra.async.adapter.out.producer;

import java.util.Objects;
import java.util.UUID;

import dev.vra.inventory.application.port.ReservationOutboxPort;
import dev.vra.inventory.domain.InventoryReservation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class JdbcReservationOutboxPublisher implements ReservationOutboxPort {
    private static final String TARGET_CODE = "RESERVATION_PROJECTION";
    private final JdbcClient jdbcClient;

    public JdbcReservationOutboxPublisher(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void publish(InventoryReservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox publication requires the reservation transaction");
        }
        UUID eventId;
        do {
            eventId = UUID.randomUUID();
        } while (eventId.equals(reservation.reservationId()));
        UUID publishedId = jdbcClient.sql("SELECT vra.async_publish_reservation(:eventId, :reservationId, :targetCode)")
                .param("eventId", eventId)
                .param("reservationId", reservation.reservationId())
                .param("targetCode", TARGET_CODE)
                .query(UUID.class)
                .single();
        if (!eventId.equals(publishedId)) {
            throw new IllegalStateException("Outbox publication returned a different event ID");
        }
    }
}
