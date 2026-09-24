package dev.vra.poc00.kotlin.domain

import java.time.Instant
import java.util.ArrayList
import java.util.Collections

/**
 * Immutable order snapshot. The defensive copy is wrapped as unmodifiable so both
 * mutation of the caller's collection and a Kotlin MutableList downcast are rejected.
 */
class Order(
    val id: OrderId,
    val state: OrderState,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val cancellationReason: String?,
    val reasonCode: OrderReasonCode?,
    items: List<OrderItem>,
    val version: Long
) {
    val items: List<OrderItem> = Collections.unmodifiableList(ArrayList(items))

    init {
        require(this.items.isNotEmpty() && version >= 0) { "Invalid order." }
        require((state == OrderState.CONFIRMED) == (confirmedAt != null)) {
            "Confirmation timestamp must match state."
        }
        require(confirmedAt == null || !confirmedAt.isBefore(createdAt)) {
            "Confirmation cannot precede creation."
        }
        require(cancellationReason == null ||
            (state == OrderState.CANCELLED && cancellationReason.isNotBlank() && cancellationReason.length <= 500)) {
            "Invalid cancellation reason."
        }
        when (state) {
            OrderState.CREATED, OrderState.PENDING_PAYMENT, OrderState.CONFIRMED ->
                require(reasonCode == null) { "Reason code is invalid for order state." }
            OrderState.CANCELLED ->
                require(reasonCode != OrderReasonCode.PAYMENT_TIMEOUT) {
                    "Reason code is invalid for cancelled order."
                }
            OrderState.EXPIRED ->
                require(reasonCode == OrderReasonCode.PAYMENT_TIMEOUT) {
                    "Expired orders require PAYMENT_TIMEOUT."
                }
        }
    }

    fun pendingPayment(): Order {
        state.requireTransitionTo(OrderState.PENDING_PAYMENT)
        return Order(id, OrderState.PENDING_PAYMENT, createdAt, null, null, null, items, Math.incrementExact(version))
    }

    fun confirm(now: Instant): Order {
        state.requireTransitionTo(OrderState.CONFIRMED)
        return Order(id, OrderState.CONFIRMED, createdAt, now, null, null, items, Math.incrementExact(version))
    }

    fun cancel(reason: String, reasonCode: OrderReasonCode): Order {
        state.requireTransitionTo(OrderState.CANCELLED)
        return Order(id, OrderState.CANCELLED, createdAt, null, reason, reasonCode, items, Math.incrementExact(version))
    }

    fun expire(): Order {
        state.requireTransitionTo(OrderState.EXPIRED)
        return Order(id, OrderState.EXPIRED, createdAt, null, null, OrderReasonCode.PAYMENT_TIMEOUT,
            items, Math.incrementExact(version))
    }

    companion object {
        fun create(id: OrderId, now: Instant, items: List<OrderItem>) =
            Order(id, OrderState.CREATED, now, null, null, null, items, 0)
    }
}
