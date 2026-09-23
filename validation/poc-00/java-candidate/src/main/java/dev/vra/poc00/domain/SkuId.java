package dev.vra.poc00.domain;

import java.util.Objects;
import java.util.UUID;

public record SkuId(UUID value) {
    public SkuId { Objects.requireNonNull(value, "value"); }
}
