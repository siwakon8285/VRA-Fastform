package dev.vra.poc00.kotlin.domain

enum class OrderState {
    CREATED, PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED;

    fun requireTransitionTo(next: OrderState) {
        val allowed = when (this) {
            CREATED -> next == PENDING_PAYMENT
            PENDING_PAYMENT -> next == CONFIRMED || next == CANCELLED || next == EXPIRED
            CONFIRMED, CANCELLED, EXPIRED -> false
        }
        if (!allowed) throw DomainFailure.InvalidTransition()
    }
}
