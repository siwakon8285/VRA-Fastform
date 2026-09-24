package dev.vra.poc00.kotlin.interfaces

import dev.vra.poc00.kotlin.application.ReservationApplicationService
import dev.vra.poc00.kotlin.domain.InventoryBalance
import dev.vra.poc00.kotlin.domain.SkuId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/poc/reservations")
class ReservationController(private val service: ReservationApplicationService) {
    data class Request(
        @field:NotNull val skuId: UUID,
        @field:NotNull @field:Positive val quantity: Int
    )

    data class Response(
        val skuId: UUID,
        val quantity: Int,
        val onHand: Long,
        val reserved: Long,
        val available: Long
    )

    @PostMapping
    fun reserve(@Valid @RequestBody request: Request): Response {
        val balance = service.reserve(SkuId(request.skuId), request.quantity)
        return Response(balance.skuId.value, request.quantity, balance.onHand, balance.reserved, balance.available)
    }
}
