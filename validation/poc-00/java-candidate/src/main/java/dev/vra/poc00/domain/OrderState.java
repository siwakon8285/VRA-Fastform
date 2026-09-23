package dev.vra.poc00.domain;

public enum OrderState {
    CREATED, PENDING_PAYMENT, CONFIRMED, CANCELLED;

    public void requireTransitionTo(OrderState next) {
        boolean allowed = switch (this) {
            case CREATED -> next == PENDING_PAYMENT;
            case PENDING_PAYMENT -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED, CANCELLED -> false;
        };
        if (!allowed) throw new DomainFailure.InvalidTransition();
    }
}
