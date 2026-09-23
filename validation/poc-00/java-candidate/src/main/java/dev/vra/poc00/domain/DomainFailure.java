package dev.vra.poc00.domain;

/** Expected domain rejections; adapters choose transport status codes. */
public abstract sealed class DomainFailure extends RuntimeException {
    private DomainFailure(String message) { super(message); }

    public static final class InsufficientStock extends DomainFailure {
        public InsufficientStock() { super("Insufficient stock."); }
    }
    public static final class InvalidQuantity extends DomainFailure {
        public InvalidQuantity() { super("Quantity must be positive."); }
    }
    public static final class InvalidTransition extends DomainFailure {
        public InvalidTransition() { super("Order transition is not allowed."); }
    }
    public static final class InvalidIdempotencyKey extends DomainFailure {
        public InvalidIdempotencyKey() { super("Idempotency key must contain 1 to 128 characters."); }
    }
    public static final class CurrencyMismatch extends DomainFailure {
        public CurrencyMismatch() { super("Currencies must match."); }
    }
    public static final class UnknownSku extends DomainFailure {
        public UnknownSku() { super("SKU was not found."); }
    }
}
