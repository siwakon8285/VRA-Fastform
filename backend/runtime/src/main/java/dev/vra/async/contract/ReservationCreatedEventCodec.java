package dev.vra.async.contract;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

import dev.vra.inventory.domain.StockStatus;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Exact, deterministic wire representation for the selected creation contract. */
public final class ReservationCreatedEventCodec {
    private static final Set<String> ENVELOPE = Set.of(
            "eventId", "eventType", "schemaVersion", "occurredAt", "payload");
    private static final Set<String> PAYLOAD = Set.of(
            "reservationId", "skuId", "ownerId", "locationId", "stockStatus", "quantity");
    private final ObjectMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public String encode(ReservationCreatedEventV1 event) {
        if (event == null) {
            throw new IllegalArgumentException("Event is required");
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("eventId", event.eventId().toString());
        root.put("eventType", event.eventType());
        root.put("schemaVersion", event.schemaVersion());
        root.put("occurredAt", event.occurredAt().toString());
        ObjectNode payload = mapper.createObjectNode();
        payload.put("reservationId", event.payload().reservationId().toString());
        payload.put("skuId", event.payload().skuId().toString());
        payload.put("ownerId", event.payload().ownerId().toString());
        payload.put("locationId", event.payload().locationId().toString());
        payload.put("stockStatus", event.payload().stockStatus().name());
        payload.put("quantity", event.payload().quantity());
        root.set("payload", payload);
        return mapper.writeValueAsString(root);
    }

    public ReservationCreatedEventV1 decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Event JSON is required");
        }
        final JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Malformed event JSON", error);
        }
        requireShape(root, ENVELOPE, "envelope");
        JsonNode payload = root.get("payload");
        requireShape(payload, PAYLOAD, "payload");
        String type = text(root, "eventType");
        if (!ReservationCreatedEventV1.EVENT_TYPE.equals(type)) {
            throw new IllegalArgumentException("Unsupported event type");
        }
        int version = integer(root, "schemaVersion");
        if (version != ReservationCreatedEventV1.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version");
        }
        Instant occurredAt;
        String timestamp = text(root, "occurredAt");
        try {
            occurredAt = Instant.parse(timestamp);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Invalid occurredAt", error);
        }
        if (!occurredAt.toString().equals(timestamp)) {
            throw new IllegalArgumentException("occurredAt must use canonical UTC instant form");
        }
        StockStatus status;
        try {
            status = StockStatus.valueOf(text(payload, "stockStatus"));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported stockStatus", error);
        }
        return new ReservationCreatedEventV1(uuid(root, "eventId"), type, version, occurredAt,
                new ReservationCreatedEventV1.Payload(
                        uuid(payload, "reservationId"), uuid(payload, "skuId"),
                        uuid(payload, "ownerId"), uuid(payload, "locationId"),
                        status, positiveLong(payload, "quantity")));
    }

    private static void requireShape(JsonNode node, Set<String> expected, String label) {
        if (node == null || !node.isObject()
                || !node.properties().stream().map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()).equals(expected)) {
            throw new IllegalArgumentException("Invalid " + label + " shape");
        }
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value.asText();
    }

    private static UUID uuid(JsonNode node, String name) {
        String value = text(node, name);
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) {
                throw new IllegalArgumentException("Noncanonical " + name);
            }
            return uuid;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid " + name, error);
        }
    }

    private static int integer(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value.intValue();
    }

    private static long positiveLong(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value.longValue();
    }
}
