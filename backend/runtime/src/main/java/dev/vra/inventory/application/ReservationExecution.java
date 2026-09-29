package dev.vra.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import dev.vra.inventory.application.port.InventoryBalanceRepository;
import dev.vra.inventory.application.port.ReservationOutboxPort;
import dev.vra.inventory.application.port.ReservationRepository;
import dev.vra.inventory.domain.InventoryKey;
import dev.vra.inventory.domain.InventoryReservation;
import org.springframework.stereotype.Component;

@Component
public class ReservationExecution {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationOutboxPort reservationOutboxPort;
    private final Clock clock;

    public ReservationExecution(
            InventoryBalanceRepository inventoryBalanceRepository,
            ReservationRepository reservationRepository,
            ReservationOutboxPort reservationOutboxPort,
            Clock clock
    ) {
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.reservationRepository = reservationRepository;
        this.reservationOutboxPort = reservationOutboxPort;
        this.clock = clock;
    }

    public ReservationDecision execute(InventoryKey inventoryKey, long quantity) {
        var attempt = inventoryBalanceRepository.reserve(inventoryKey, quantity);

        if (attempt.status()
                != InventoryBalanceRepository.ReserveStatus.RESERVED) {
            return new ReservationDecision.Rejected(switch (attempt.status()) {
                case NOT_FOUND -> ReservationFailureCode.INVENTORY_NOT_FOUND;
                case NOT_RESERVABLE -> ReservationFailureCode.INVENTORY_NOT_RESERVABLE;
                case INSUFFICIENT_STOCK -> ReservationFailureCode.INSUFFICIENT_STOCK;
                case RESERVED -> throw new IllegalStateException(
                        "Successful reservation cannot be rejected"
                );
            });
        }

        UUID reservationId = UUID.randomUUID();
        Instant createdAt = Instant.now(clock);
        InventoryReservation reservation = new InventoryReservation(
                reservationId,
                inventoryKey,
                quantity,
                createdAt
        );
        // The repository flushes the authoritative row before the DB function reads it.
        reservationRepository.save(reservation);
        reservationOutboxPort.publish(reservation);

        return new ReservationDecision.Succeeded(
                reservationId,
                attempt.newVersion()
        );
    }
}
