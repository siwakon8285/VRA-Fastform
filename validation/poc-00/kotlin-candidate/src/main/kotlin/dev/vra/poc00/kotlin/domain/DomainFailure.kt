package dev.vra.poc00.kotlin.domain

/** Expected domain rejections; HTTP status and messages remain adapter concerns. */
sealed class DomainFailure(message: String) : RuntimeException(message) {
    class InsufficientStock : DomainFailure("Insufficient stock.")
    class InvalidQuantity : DomainFailure("Quantity must be positive.")
    class InvalidTransition : DomainFailure("Order transition is not allowed.")
    class InvalidIdempotencyKey : DomainFailure("Idempotency key must contain 1 to 128 characters.")
    class CurrencyMismatch : DomainFailure("Currencies must match.")
    class UnknownSku : DomainFailure("SKU was not found.")
}
