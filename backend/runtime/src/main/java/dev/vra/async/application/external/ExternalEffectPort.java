package dev.vra.async.application.external;

import java.util.UUID;

import dev.vra.async.contract.ReservationCreatedEventV1.Payload;

/** Validation-only external boundary; eventId is the stable operation identity. */
public interface ExternalEffectPort {
    enum ExecuteOutcome { CONFIRMED_SUCCEEDED, UNKNOWN_OUTCOME, CONFLICT }

    enum Observation { CONFIRMED_SUCCEEDED, CONFIRMED_NO_EFFECT, INDETERMINATE }

    ExecuteOutcome execute(UUID eventId, Payload payload);

    Observation observe(UUID eventId);
}
