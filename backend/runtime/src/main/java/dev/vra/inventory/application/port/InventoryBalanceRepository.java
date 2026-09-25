package dev.vra.inventory.application.port;

import dev.vra.inventory.domain.InventoryKey;

public interface InventoryBalanceRepository {

    ReserveAttempt reserve(
            InventoryKey inventoryKey,
            long quantity
    );

    enum ReserveStatus {
        RESERVED,
        NOT_FOUND,
        NOT_RESERVABLE,
        INSUFFICIENT_STOCK
    }

    record ReserveAttempt(
            ReserveStatus status,
            Long newVersion
    ) {

        public static ReserveAttempt reserved(long newVersion) {
            return new ReserveAttempt(ReserveStatus.RESERVED, newVersion);
        }

        public static ReserveAttempt rejected(ReserveStatus status) {
            if (status == ReserveStatus.RESERVED) {
                throw new IllegalArgumentException(
                        "Use reserved(newVersion) for a successful attempt"
                );
            }

            return new ReserveAttempt(status, null);
        }
    }
}
