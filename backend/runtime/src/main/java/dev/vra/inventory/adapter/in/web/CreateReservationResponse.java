package dev.vra.inventory.adapter.in.web;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateReservationResponse(
        @JsonProperty("reservation_id") UUID reservationId,
        @JsonProperty("inventory_version") long inventoryVersion,
        @JsonProperty("request_id") String requestId
) {
}
