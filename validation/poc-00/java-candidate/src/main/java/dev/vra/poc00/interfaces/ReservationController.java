package dev.vra.poc00.interfaces;

import dev.vra.poc00.application.ReservationApplicationService;
import dev.vra.poc00.domain.SkuId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/poc/reservations")
public class ReservationController {
    private final ReservationApplicationService service;
    public ReservationController(ReservationApplicationService service) { this.service = service; }

    public record Request(@NotNull UUID skuId, @NotNull @Positive Integer quantity) {}
    public record Response(UUID skuId, int quantity, long onHand, long reserved, long available) {}

    @PostMapping
    public Response reserve(@Valid @RequestBody Request request) {
        var balance = service.reserve(new SkuId(request.skuId()), request.quantity());
        return new Response(balance.skuId().value(), request.quantity(),
                balance.onHand(), balance.reserved(), balance.available());
    }
}
