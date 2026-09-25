package dev.vra.inventory.application;

import java.time.Clock;
import java.time.Instant;
import dev.vra.inventory.application.port.ReservationIdempotencyRepository;
import dev.vra.inventory.application.port.ReservationIdempotencyRepository.ClaimResult;
import dev.vra.inventory.application.ReservationRequestFingerprint.Fingerprint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotentReservationApplicationService {

    private final ReservationIdempotencyRepository idempotencyRepository;
    private final ReservationRequestFingerprint requestFingerprint;
    private final ReservationExecution reservationExecution;
    private final Clock clock;

    public IdempotentReservationApplicationService(
            ReservationIdempotencyRepository idempotencyRepository,
            ReservationRequestFingerprint requestFingerprint,
            ReservationExecution reservationExecution,
            Clock clock
    ) {
        this.idempotencyRepository = idempotencyRepository;
        this.requestFingerprint = requestFingerprint;
        this.reservationExecution = reservationExecution;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IdempotentReservationResult reserve(
            IdempotentReserveInventoryCommand command
    ) {
        validate(command);

        Fingerprint fingerprint = requestFingerprint.compute(
                command.inventoryKey(), command.quantity()
        );
        Instant createdAt = Instant.now(clock);
        ClaimResult claim = idempotencyRepository.claimOrResolve(
                command.actorScope(), command.idempotencyKey(),
                fingerprint, createdAt
        );

        if (claim instanceof ClaimResult.ReplayedSuccess replay) {
            return new IdempotentReservationResult.Succeeded(
                    replay.reservationId(), replay.inventoryVersion()
            );
        }
        if (claim instanceof ClaimResult.ReplayedRejection replay) {
            return new IdempotentReservationResult.Rejected(replay.code());
        }
        if (claim instanceof ClaimResult.FingerprintConflict) {
            return new IdempotentReservationResult.Conflict(
                    IdempotentReservationResult.IdempotencyFailureCode.IDEMPOTENCY_KEY_REUSED
            );
        }
        if (!(claim instanceof ClaimResult.Claimed)) {
            throw new IllegalStateException("Unknown idempotency claim outcome");
        }

        ReservationDecision decision = reservationExecution.execute(
                command.inventoryKey(), command.quantity()
        );
        if (decision instanceof ReservationDecision.Succeeded success) {
            idempotencyRepository.completeSucceeded(
                    command.actorScope(), command.idempotencyKey(), fingerprint,
                    success.reservationId(), success.inventoryVersion(), Instant.now(clock)
            );
            return new IdempotentReservationResult.Succeeded(
                    success.reservationId(), success.inventoryVersion()
            );
        }
        if (decision instanceof ReservationDecision.Rejected rejected) {
            idempotencyRepository.completeRejected(
                    command.actorScope(), command.idempotencyKey(), fingerprint,
                    rejected.code(), Instant.now(clock)
            );
            return new IdempotentReservationResult.Rejected(rejected.code());
        }
        throw new IllegalStateException("Unknown reservation decision");
    }

    private static void validate(IdempotentReserveInventoryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Reservation command is required");
        }
        validateIdentity(command.actorScope(), "actorScope");
        validateIdentity(command.idempotencyKey(), "idempotencyKey");
        if (command.inventoryKey() == null) {
            throw new IllegalArgumentException("inventoryKey is required");
        }
        if (command.quantity() <= 0) {
            throw new ReservationFailureException(
                    ReservationFailureCode.INVALID_QUANTITY,
                    "Reservation quantity must be greater than zero"
            );
        }
    }

    private static void validateIdentity(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.codePointCount(0, value.length()) > 128) {
            throw new IllegalArgumentException(field + " exceeds 128 code points");
        }
    }
}
