package dev.vra.inventory.application;

import java.time.Clock;
import java.time.Instant;

import dev.vra.inventory.application.port.InventoryBalanceRepository;
import dev.vra.inventory.application.port.ReservationRepository;
import dev.vra.inventory.domain.InventoryReservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationApplicationService {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public ReservationApplicationService(
            InventoryBalanceRepository inventoryBalanceRepository,
            ReservationRepository reservationRepository,
            Clock clock
    ) {
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationResult reserve(ReserveInventoryCommand command) {
        if (command.quantity() <= 0) {
            throw new ReservationFailureException(
                    ReservationFailureCode.INVALID_QUANTITY,
                    "Reservation quantity must be greater than zero"
            );
        }

        if (!command.inventoryKey().stockStatus().isReservable()) {
            throw new ReservationFailureException(
                    ReservationFailureCode.INVENTORY_NOT_RESERVABLE,
                    "Inventory status is not reservable"
            );
        }

        var attempt = inventoryBalanceRepository.reserve(
                command.inventoryKey(),
                command.quantity(),
                command.expectedVersion()
        );

        if (attempt.status()
                != InventoryBalanceRepository.ReserveStatus.RESERVED) {
            throw rejected(attempt.status());
        }

        var reservation = new InventoryReservation(
                command.reservationId(),
                command.inventoryKey(),
                command.quantity(),
                Instant.now(clock)
        );

        reservationRepository.save(reservation);

        return new ReservationResult(
                reservation.reservationId(),
                attempt.newVersion()
        );
    }

    private ReservationFailureException rejected(
            InventoryBalanceRepository.ReserveStatus status
    ) {
        return switch (status) {
            case NOT_FOUND -> new ReservationFailureException(
                    ReservationFailureCode.INVENTORY_NOT_FOUND,
                    "Inventory balance was not found"
            );
            case NOT_RESERVABLE -> new ReservationFailureException(
                    ReservationFailureCode.INVENTORY_NOT_RESERVABLE,
                    "Inventory status is not reservable"
            );
            case INSUFFICIENT_STOCK -> new ReservationFailureException(
                    ReservationFailureCode.INSUFFICIENT_STOCK,
                    "Insufficient stock"
            );
            case VERSION_CONFLICT -> new ReservationFailureException(
                    ReservationFailureCode.VERSION_CONFLICT,
                    "Inventory version conflict"
            );
            case RESERVED -> throw new IllegalStateException(
                    "Successful reservation cannot be mapped to a failure"
            );
        };
    }
}
