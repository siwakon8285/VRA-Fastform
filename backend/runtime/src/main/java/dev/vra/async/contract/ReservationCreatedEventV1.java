package dev.vra.async.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.vra.inventory.domain.StockStatus;

public record ReservationCreatedEventV1(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        Payload payload
) {
    public static final String EVENT_TYPE = "inventory.reservation.created";
    public static final int SCHEMA_VERSION = 1;

    public ReservationCreatedEventV1 {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        if (!EVENT_TYPE.equals(eventType)) {
            throw new IllegalArgumentException("Unsupported event type");
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version");
        }
        if (eventId.equals(payload.reservationId())) {
            throw new IllegalArgumentException("Event and reservation IDs must differ");
        }
    }

    public record Payload(
            UUID reservationId,
            UUID skuId,
            UUID ownerId,
            UUID locationId,
            StockStatus stockStatus,
            long quantity
    ) {
        public Payload {
            Objects.requireNonNull(reservationId, "reservationId");
            Objects.requireNonNull(skuId, "skuId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(locationId, "locationId");
            Objects.requireNonNull(stockStatus, "stockStatus");
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
        }
    }
}
