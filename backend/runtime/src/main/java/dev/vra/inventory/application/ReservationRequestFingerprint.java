package dev.vra.inventory.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import dev.vra.inventory.domain.InventoryKey;
import org.springframework.stereotype.Component;

@Component
public class ReservationRequestFingerprint {
    public static final short VERSION = 1;

    public Fingerprint compute(InventoryKey inventoryKey, long quantity) {
        Objects.requireNonNull(inventoryKey, "inventoryKey");
        String canonical = "v1\n"
                + "skuId=" + inventoryKey.skuId().toString() + "\n"
                + "ownerId=" + inventoryKey.ownerId().toString() + "\n"
                + "locationId=" + inventoryKey.locationId().toString() + "\n"
                + "stockStatus=" + inventoryKey.stockStatus().name() + "\n"
                + "quantity=" + Long.toString(quantity);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new Fingerprint(VERSION, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public record Fingerprint(short version, String value) {
        public Fingerprint {
            Objects.requireNonNull(value, "value");
            if (!value.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Fingerprint must be 64 lowercase hexadecimal characters");
            }
        }
    }
}
