package dev.vra.poc00.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Order(OrderId id, OrderState state, Instant createdAt,
                    Optional<Instant> confirmedAt, Optional<String> cancellationReason,
                    Optional<OrderReasonCode> reasonCode, List<OrderItem> items, long version) {
    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        Objects.requireNonNull(cancellationReason, "cancellationReason");
        Objects.requireNonNull(reasonCode, "reasonCode");
        items = List.copyOf(items);
        if (items.isEmpty() || version < 0) throw new IllegalArgumentException("Invalid order.");
        if ((state == OrderState.CONFIRMED) != confirmedAt.isPresent())
            throw new IllegalArgumentException("Confirmation timestamp must match state.");
        if (confirmedAt.filter(time -> time.isBefore(createdAt)).isPresent())
            throw new IllegalArgumentException("Confirmation cannot precede creation.");
        if (cancellationReason.isPresent() && (state != OrderState.CANCELLED
                || cancellationReason.get().isBlank() || cancellationReason.get().length() > 500))
            throw new IllegalArgumentException("Invalid cancellation reason.");
        switch (state) {
            case CREATED, PENDING_PAYMENT, CONFIRMED -> {
                if (reasonCode.isPresent()) throw new IllegalArgumentException("Reason code is invalid for order state.");
            }
            case CANCELLED -> {
                if (reasonCode.filter(code -> code == OrderReasonCode.PAYMENT_TIMEOUT).isPresent())
                    throw new IllegalArgumentException("Reason code is invalid for cancelled order.");
            }
            case EXPIRED -> {
                if (!reasonCode.equals(Optional.of(OrderReasonCode.PAYMENT_TIMEOUT)))
                    throw new IllegalArgumentException("Expired orders require PAYMENT_TIMEOUT.");
            }
        }
    }
    public static Order create(OrderId id, Instant now, List<OrderItem> items) {
        return new Order(id, OrderState.CREATED, now, Optional.empty(), Optional.empty(), Optional.empty(), items, 0);
    }
    public Order pendingPayment() {
        state.requireTransitionTo(OrderState.PENDING_PAYMENT);
        return new Order(id, OrderState.PENDING_PAYMENT, createdAt, Optional.empty(),
                Optional.empty(), Optional.empty(), items, Math.incrementExact(version));
    }
    public Order confirm(Instant now) {
        state.requireTransitionTo(OrderState.CONFIRMED);
        return new Order(id, OrderState.CONFIRMED, createdAt, Optional.of(now),
                Optional.empty(), Optional.empty(), items, Math.incrementExact(version));
    }
    public Order cancel(String reason, OrderReasonCode reasonCode) {
        state.requireTransitionTo(OrderState.CANCELLED);
        return new Order(id, OrderState.CANCELLED, createdAt, Optional.empty(),
                Optional.of(reason), Optional.of(reasonCode), items, Math.incrementExact(version));
    }
    public Order expire() {
        state.requireTransitionTo(OrderState.EXPIRED);
        return new Order(id, OrderState.EXPIRED, createdAt, Optional.empty(),
                Optional.empty(), Optional.of(OrderReasonCode.PAYMENT_TIMEOUT), items, Math.incrementExact(version));
    }
}
