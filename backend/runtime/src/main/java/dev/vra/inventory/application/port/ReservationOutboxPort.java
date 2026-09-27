package dev.vra.inventory.application.port;

import dev.vra.inventory.domain.InventoryReservation;

public interface ReservationOutboxPort {
    void publish(InventoryReservation reservation);
}
