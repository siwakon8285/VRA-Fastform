package dev.vra.poc00.domain;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {
    public OrderId { Objects.requireNonNull(value, "value"); }
}
