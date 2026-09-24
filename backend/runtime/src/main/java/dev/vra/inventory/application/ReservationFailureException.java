package dev.vra.inventory.application;

import java.util.Objects;

public final class ReservationFailureException extends RuntimeException {

    private final ReservationFailureCode code;

    public ReservationFailureException(
            ReservationFailureCode code,
            String message
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ReservationFailureCode code() {
        return code;
    }
}
