package dev.vra.inventory.application.port;

import dev.vra.inventory.domain.InventoryReservation;

public interface ReservationRepository {

    void save(InventoryReservation reservation);
}
