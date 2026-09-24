package dev.vra.inventory.adapter.in.web;

import java.util.UUID;

import dev.vra.inventory.domain.StockStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateReservationRequest(
        @NotNull UUID skuId,
        @NotNull UUID ownerId,
        @NotNull UUID locationId,
        @NotNull StockStatus stockStatus,
        @NotNull @Positive Long quantity,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
