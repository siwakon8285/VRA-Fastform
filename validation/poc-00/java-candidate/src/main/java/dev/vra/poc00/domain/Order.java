package dev.vra.poc00.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Order(OrderId id, OrderState state, Instant createdAt,
                    Optional<Instant> confirmedAt, Optional<String> cancellationReason,
                    List<OrderItem> items, long version) {
    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        Objects.requireNonNull(cancellationReason, "cancellationReason");
        items = List.copyOf(items);
        if (items.isEmpty() || version < 0) throw new IllegalArgumentException("Invalid order.");
        if ((state == OrderState.CONFIRMED) != confirmedAt.isPresent())
            throw new IllegalArgumentException("Confirmation timestamp must match state.");
        if (confirmedAt.filter(time -> time.isBefore(createdAt)).isPresent())
            throw new IllegalArgumentException("Confirmation cannot precede creation.");
        if (cancellationReason.isPresent() && (state != OrderState.CANCELLED
                || cancellationReason.get().isBlank() || cancellationReason.get().length() > 500))
            throw new IllegalArgumentException("Invalid cancellation reason.");
    }
    public static Order create(OrderId id, Instant now, List<OrderItem> items) {
        return new Order(id, OrderState.CREATED, now, Optional.empty(), Optional.empty(), items, 0);
    }
    public Order pendingPayment() {
        state.requireTransitionTo(OrderState.PENDING_PAYMENT);
        return new Order(id, OrderState.PENDING_PAYMENT, createdAt, Optional.empty(),
                Optional.empty(), items, Math.incrementExact(version));
    }
    public Order confirm(Instant now) {
        state.requireTransitionTo(OrderState.CONFIRMED);
        return new Order(id, OrderState.CONFIRMED, createdAt, Optional.of(now),
                Optional.empty(), items, Math.incrementExact(version));
    }
    public Order cancel(String reason) {
        state.requireTransitionTo(OrderState.CANCELLED);
        return new Order(id, OrderState.CANCELLED, createdAt, Optional.empty(),
                Optional.of(reason), items, Math.incrementExact(version));
    }
}
