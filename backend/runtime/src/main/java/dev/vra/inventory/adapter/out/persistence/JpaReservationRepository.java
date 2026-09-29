package dev.vra.inventory.adapter.out.persistence;

import dev.vra.inventory.application.port.ReservationRepository;
import dev.vra.inventory.domain.InventoryReservation;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class JpaReservationRepository implements ReservationRepository {

    private final EntityManager entityManager;

    public JpaReservationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void save(InventoryReservation reservation) {
        entityManager.persist(
                new ReservationEntity(
                        reservation.reservationId(),
                        reservation.inventoryKey().skuId(),
                        reservation.inventoryKey().ownerId(),
                        reservation.inventoryKey().locationId(),
                        reservation.inventoryKey().stockStatus().name(),
                        reservation.quantity(),
                        reservation.createdAt()
                )
        );
        entityManager.flush();
    }
}
