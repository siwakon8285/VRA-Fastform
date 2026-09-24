package dev.vra.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.vra.inventory.application.port.InventoryBalanceRepository;
import dev.vra.inventory.application.port.ReservationRepository;
import dev.vra.inventory.domain.InventoryKey;
import dev.vra.inventory.domain.StockStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void invalidQuantityIsTypedFailureBeforePersistence() {
        AtomicBoolean balanceCalled = new AtomicBoolean();
        AtomicBoolean reservationCalled = new AtomicBoolean();

        InventoryBalanceRepository balanceRepository =
                (key, quantity, expectedVersion) -> {
                    balanceCalled.set(true);
                    return InventoryBalanceRepository.ReserveAttempt.reserved(1);
                };

        ReservationRepository reservationRepository =
                reservation -> reservationCalled.set(true);

        var service = new ReservationApplicationService(
                balanceRepository,
                reservationRepository,
                CLOCK
        );

        var error = assertThrows(
                ReservationFailureException.class,
                () -> service.reserve(
                        new ReserveInventoryCommand(
                                UUID.randomUUID(),
                                key(StockStatus.AVAILABLE),
                                0,
                                0
                        )
                )
        );

        assertEquals(
                ReservationFailureCode.INVALID_QUANTITY,
                error.code()
        );
        assertFalse(balanceCalled.get());
        assertFalse(reservationCalled.get());
    }

    @Test
    void quarantinedInventoryIsRejectedBeforePersistence() {
        AtomicBoolean balanceCalled = new AtomicBoolean();
        AtomicBoolean reservationCalled = new AtomicBoolean();

        InventoryBalanceRepository balanceRepository =
                (key, quantity, expectedVersion) -> {
                    balanceCalled.set(true);
                    return InventoryBalanceRepository.ReserveAttempt.reserved(1);
                };

        ReservationRepository reservationRepository =
                reservation -> reservationCalled.set(true);

        var service = new ReservationApplicationService(
                balanceRepository,
                reservationRepository,
                CLOCK
        );

        var error = assertThrows(
                ReservationFailureException.class,
                () -> service.reserve(
                        new ReserveInventoryCommand(
                                UUID.randomUUID(),
                                key(StockStatus.QUARANTINED),
                                1,
                                0
                        )
                )
        );

        assertEquals(
                ReservationFailureCode.INVENTORY_NOT_RESERVABLE,
                error.code()
        );
        assertFalse(balanceCalled.get());
        assertFalse(reservationCalled.get());
    }

    private static InventoryKey key(StockStatus status) {
        return new InventoryKey(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status
        );
    }
}
