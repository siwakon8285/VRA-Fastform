package dev.vra.poc00.domain;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || value.isBlank() || value.length() > 128)
            throw new DomainFailure.InvalidIdempotencyKey();
    }
}
