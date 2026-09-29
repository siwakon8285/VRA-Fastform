package dev.vra.inventory.adapter.in.web;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import dev.vra.inventory.domain.StockStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateReservationRequest(
        @NotNull UUID skuId,
        @NotNull UUID ownerId,
        @NotNull UUID locationId,
        @NotNull StockStatus stockStatus,
        @NotNull @Positive Long quantity
) {
    @JsonAnySetter
    void rejectUnknownProperty(String propertyName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown request property: " + propertyName);
    }
}
