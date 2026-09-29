package dev.vra.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationApplicationService {

    private final ReservationExecution reservationExecution;

    public ReservationApplicationService(ReservationExecution reservationExecution) {
        this.reservationExecution = reservationExecution;
    }

    @Transactional
    public ReservationResult reserve(ReserveInventoryCommand command) {
        if (command.quantity() <= 0) {
            throw new ReservationFailureException(
                    ReservationFailureCode.INVALID_QUANTITY,
                    "Reservation quantity must be greater than zero"
            );
        }

        ReservationDecision decision = reservationExecution.execute(
                command.inventoryKey(),
                command.quantity()
        );

        if (decision instanceof ReservationDecision.Succeeded success) {
            return new ReservationResult(
                    success.reservationId(),
                    success.inventoryVersion()
            );
        }

        ReservationFailureCode code =
                ((ReservationDecision.Rejected) decision).code();

        throw new ReservationFailureException(code, switch (code) {
            case INVENTORY_NOT_FOUND -> "Inventory balance was not found";
            case INVENTORY_NOT_RESERVABLE -> "Inventory status is not reservable";
            case INSUFFICIENT_STOCK -> "Insufficient stock";
            case INVALID_QUANTITY -> throw new IllegalStateException(
                    "Invalid quantity is not an authoritative stock decision"
            );
        });
    }
}
