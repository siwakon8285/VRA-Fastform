package dev.vra.inventory.adapter.in.web;

import dev.vra.inventory.application.ReservationApplicationService;
import dev.vra.inventory.application.ReserveInventoryCommand;
import dev.vra.inventory.domain.InventoryKey;
import dev.vra.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/reservations")
public class InventoryReservationController {

    private final ReservationApplicationService reservationApplicationService;

    public InventoryReservationController(
            ReservationApplicationService reservationApplicationService
    ) {
        this.reservationApplicationService = reservationApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateReservationResponse create(
            @Valid @RequestBody CreateReservationRequest request,
            HttpServletRequest httpRequest
    ) {
        var result = reservationApplicationService.reserve(
                new ReserveInventoryCommand(
                        new InventoryKey(
                                request.skuId(),
                                request.ownerId(),
                                request.locationId(),
                                request.stockStatus()
                        ),
                        request.quantity()
                )
        );

        return new CreateReservationResponse(
                result.reservationId(),
                result.inventoryVersion(),
                RequestIdFilter.requestId(httpRequest)
        );
    }
}
