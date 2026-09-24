package dev.vra.poc00.domain;

public enum OrderState {
    CREATED, PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED;

    public void requireTransitionTo(OrderState next) {
        boolean allowed = switch (this) {
            case CREATED -> next == PENDING_PAYMENT;
            case PENDING_PAYMENT -> next == CONFIRMED || next == CANCELLED || next == EXPIRED;
            case CONFIRMED, CANCELLED, EXPIRED -> false;
        };
        if (!allowed) throw new DomainFailure.InvalidTransition();
    }
}
