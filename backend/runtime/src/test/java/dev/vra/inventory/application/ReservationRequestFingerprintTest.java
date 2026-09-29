package dev.vra.inventory.application;

import java.util.UUID;

import dev.vra.inventory.domain.InventoryKey;
import dev.vra.inventory.domain.StockStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReservationRequestFingerprintTest {
    private final ReservationRequestFingerprint fingerprints = new ReservationRequestFingerprint();

    @Test
    void goldenVectorA() {
        assertGolden("00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000002",
                "00000000-0000-0000-0000-000000000003", StockStatus.AVAILABLE, 1,
                "f1797a0e7b3708f205d83c381fbe459bfd6dc26e196d64063080fae8a0575a34");
    }

    @Test
    void goldenVectorB() {
        assertGolden("123e4567-e89b-12d3-a456-426614174000",
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "11111111-2222-3333-4444-555555555555", StockStatus.QUARANTINED, 42,
                "189db9072e0ab0d5bc61cb7350450a5b258713791b8dc4a47e23b7d3f49bbbc8");
    }

    @Test
    void goldenVectorC() {
        assertGolden("ffffffff-ffff-ffff-ffff-ffffffffffff",
                "00000000-0000-0000-0000-000000000000",
                "7f3a2b1c-4d5e-6789-abcd-ef0123456789", StockStatus.AVAILABLE, Long.MAX_VALUE,
                "59c3a44135d157e8e6754d2fbd78c5515996326283e5fdf9f471da63c2125cc9");
    }

    @Test
    void rejectsMalformedDigestValues() {
        assertThrows(NullPointerException.class,
                () -> new ReservationRequestFingerprint.Fingerprint((short) 1, null));
        for (String value : new String[]{"", "a".repeat(63), "a".repeat(65),
                "A".repeat(64), "g".repeat(64)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReservationRequestFingerprint.Fingerprint((short) 1, value));
        }
    }

    @Test
    void quantityValidationIsNotFingerprintResponsibility() {
        InventoryKey key = new InventoryKey(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), StockStatus.AVAILABLE);
        assertNotEquals(fingerprints.compute(key, 0), fingerprints.compute(key, -1));
        assertThrows(NullPointerException.class, () -> fingerprints.compute(null, 1));
    }

    private void assertGolden(String sku, String owner, String location,
                              StockStatus status, long quantity, String expected) {
        var fingerprint = fingerprints.compute(new InventoryKey(UUID.fromString(sku),
                UUID.fromString(owner), UUID.fromString(location), status), quantity);
        assertEquals(1, fingerprint.version());
        assertEquals(expected, fingerprint.value());
        assertEquals(64, fingerprint.value().length());
        assertTrue(fingerprint.value().matches("[0-9a-f]{64}"));
    }
}
