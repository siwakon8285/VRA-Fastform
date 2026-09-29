package dev.vra.inventory.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryReservationTest {

    @Test
    void rejectsNonPositiveQuantity() {
        var key = new InventoryKey(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                StockStatus.AVAILABLE
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryReservation(
                        UUID.randomUUID(),
                        key,
                        0,
                        Instant.parse("2026-01-01T00:00:00Z")
                )
        );
    }
}
