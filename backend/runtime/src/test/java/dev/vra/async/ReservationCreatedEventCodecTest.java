package dev.vra.async;

import java.time.Instant;
import java.util.UUID;

import dev.vra.async.contract.ReservationCreatedEventCodec;
import dev.vra.async.contract.ReservationCreatedEventV1;
import dev.vra.inventory.domain.StockStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationCreatedEventCodecTest {
    private final ReservationCreatedEventCodec codec = new ReservationCreatedEventCodec();
    private final UUID eventId = UUID.fromString("a01b02c3-d4e5-4f67-89ab-cdef01234567");
    private final UUID reservationId = UUID.fromString("b01b02c3-d4e5-4f67-89ab-cdef01234567");

    @Test
    void exactV1WireShapeIsDeterministicAndRoundTrips() {
        ReservationCreatedEventV1 event = event();
        String expected = """
                {"eventId":"a01b02c3-d4e5-4f67-89ab-cdef01234567","eventType":"inventory.reservation.created","schemaVersion":1,"occurredAt":"2026-09-27T01:02:03.123456Z","payload":{"reservationId":"b01b02c3-d4e5-4f67-89ab-cdef01234567","skuId":"c01b02c3-d4e5-4f67-89ab-cdef01234567","ownerId":"d01b02c3-d4e5-4f67-89ab-cdef01234567","locationId":"e01b02c3-d4e5-4f67-89ab-cdef01234567","stockStatus":"AVAILABLE","quantity":7}}
                """.trim();
        assertEquals(expected, codec.encode(event));
        assertEquals(expected, codec.encode(event));
        assertEquals(event, codec.decode(expected));
    }

    @Test
    void rejectsMissingNullExtraDuplicateAndMalformedFields() {
        String valid = codec.encode(event());
        for (String invalid : new String[] {
                valid.replace("\"eventType\":\"inventory.reservation.created\",", ""),
                valid.replace("\"eventType\":\"inventory.reservation.created\"", "\"eventType\":null"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.replace("\"eventType\":\"inventory.reservation.created\"", "\"eventType\":\"inventory.reservation.changed\""),
                valid.replace("\"quantity\":7", "\"quantity\":7.5"),
                valid.replace("\"quantity\":7", "\"quantity\":0"),
                valid.replace("\"quantity\":7", "\"quantity\":\"7\""),
                valid.replace("\"quantity\":7", "\"quantity\":7,\"extra\":true"),
                valid.replace("\"eventId\":\"a01b", "\"extra\":true,\"eventId\":\"a01b"),
                valid.replace("\"eventId\":\"a01b", "\"eventId\":\"A01B"),
                valid.replace("\"occurredAt\":\"2026-09-27T01:02:03.123456Z\"", "\"occurredAt\":\"2026-09-27T08:02:03.123456+07:00\""),
                valid.replace("\"stockStatus\":\"AVAILABLE\"", "\"stockStatus\":\"UNKNOWN\""),
                valid.replace("\"quantity\":7", "\"quantity\":7,\"quantity\":8"),
                valid + " {}",
                "{not json}"
        }) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(invalid), invalid);
        }
    }

    @Test
    void rejectsInvalidObjectsBeforeEncoding() {
        ReservationCreatedEventV1.Payload payload = event().payload();
        assertThrows(IllegalArgumentException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> new ReservationCreatedEventV1(
                null, ReservationCreatedEventV1.EVENT_TYPE, 1, Instant.now(), payload));
        assertThrows(NullPointerException.class, () -> new ReservationCreatedEventV1(
                eventId, ReservationCreatedEventV1.EVENT_TYPE, 1, null, payload));
        assertThrows(NullPointerException.class, () -> new ReservationCreatedEventV1(
                eventId, ReservationCreatedEventV1.EVENT_TYPE, 1, Instant.now(), null));
        assertThrows(IllegalArgumentException.class, () -> new ReservationCreatedEventV1(
                eventId, "unsupported", 1, Instant.now(), payload));
        assertThrows(IllegalArgumentException.class, () -> new ReservationCreatedEventV1(
                eventId, ReservationCreatedEventV1.EVENT_TYPE, 2, Instant.now(), payload));
        assertThrows(IllegalArgumentException.class, () -> new ReservationCreatedEventV1.Payload(
                reservationId, eventId, eventId, eventId, StockStatus.AVAILABLE, 0));
        assertThrows(NullPointerException.class, () -> new ReservationCreatedEventV1.Payload(
                null, eventId, eventId, eventId, StockStatus.AVAILABLE, 1));
        assertThrows(NullPointerException.class, () -> new ReservationCreatedEventV1.Payload(
                reservationId, null, eventId, eventId, StockStatus.AVAILABLE, 1));
        assertThrows(NullPointerException.class, () -> new ReservationCreatedEventV1.Payload(
                reservationId, eventId, null, eventId, StockStatus.AVAILABLE, 1));
        assertThrows(NullPointerException.class, () -> new ReservationCreatedEventV1.Payload(
                reservationId, eventId, eventId, null, StockStatus.AVAILABLE, 1));
        assertThrows(NullPointerException.class, () -> new ReservationCreatedEventV1.Payload(
                reservationId, eventId, eventId, eventId, null, 1));
    }

    private ReservationCreatedEventV1 event() {
        return new ReservationCreatedEventV1(eventId,
                ReservationCreatedEventV1.EVENT_TYPE,
                ReservationCreatedEventV1.SCHEMA_VERSION,
                Instant.parse("2026-09-27T01:02:03.123456Z"),
                new ReservationCreatedEventV1.Payload(reservationId,
                        UUID.fromString("c01b02c3-d4e5-4f67-89ab-cdef01234567"),
                        UUID.fromString("d01b02c3-d4e5-4f67-89ab-cdef01234567"),
                        UUID.fromString("e01b02c3-d4e5-4f67-89ab-cdef01234567"),
                        StockStatus.AVAILABLE, 7));
    }
}
