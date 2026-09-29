package dev.vra.inventory.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationRequestFingerprint.Fingerprint;
import dev.vra.inventory.application.port.ReservationIdempotencyRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReservationIdempotencyRepository implements ReservationIdempotencyRepository {
    private final JdbcClient jdbcClient;

    public JdbcReservationIdempotencyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    @Override
    public ClaimResult claimOrResolve(String actorScope, String idempotencyKey,
                                      Fingerprint fingerprint, Instant createdAt) throws IllegalStateException {
        requireIdentity(actorScope, idempotencyKey, fingerprint);
        Objects.requireNonNull(createdAt, "createdAt");
        var claimed = identity(jdbcClient.sql("""
                INSERT INTO vra.inventory_reservation_idempotency (
                    actor_scope, idempotency_key, fingerprint_version,
                    request_fingerprint, created_at
                )
                VALUES (
                    :actorScope, :idempotencyKey, :fingerprintVersion,
                    :requestFingerprint, :createdAt
                )
                ON CONFLICT (actor_scope, idempotency_key) DO NOTHING
                RETURNING actor_scope
                """), actorScope, idempotencyKey, fingerprint)
                .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                .query(String.class).optional();
        if (claimed.isPresent()) {
            return new ClaimResult.Claimed();
        }

        var row = jdbcClient.sql("""
                SELECT fingerprint_version, request_fingerprint, outcome_status,
                       reservation_id, inventory_version, rejection_code
                FROM vra.inventory_reservation_idempotency
                WHERE actor_scope = :actorScope
                  AND idempotency_key = :idempotencyKey
                """)
                .param("actorScope", actorScope)
                .param("idempotencyKey", idempotencyKey)
                .query((rs, rowNumber) -> new StoredOutcome(
                        rs.getShort("fingerprint_version"),
                        rs.getString("request_fingerprint"),
                        rs.getString("outcome_status"),
                        rs.getObject("reservation_id", UUID.class),
                        rs.getObject("inventory_version", Long.class),
                        rs.getString("rejection_code")))
                .optional()
                .orElseThrow(() -> new IllegalStateException("Conflicting idempotency row is missing"));

        if (row.status() == null) {
            throw new IllegalStateException("Existing idempotency row has no terminal outcome");
        }
        if (!row.status().equals("SUCCEEDED") && !row.status().equals("REJECTED")) {
            throw new IllegalStateException("Unknown persisted idempotency outcome");
        }
        if (row.version() != fingerprint.version()
                || !Objects.equals(row.fingerprint(), fingerprint.value())) {
            return new ClaimResult.FingerprintConflict();
        }
        if (row.status().equals("SUCCEEDED")) {
            if (row.reservationId() == null || row.inventoryVersion() == null) {
                throw new IllegalStateException("Persisted success has incomplete result");
            }
            return new ClaimResult.ReplayedSuccess(row.reservationId(), row.inventoryVersion());
        }
        if (row.rejectionCode() == null) {
            throw new IllegalStateException("Persisted rejection has no code");
        }
        try {
            return new ClaimResult.ReplayedRejection(
                    ReservationFailureCode.valueOf(row.rejectionCode()));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Unknown persisted rejection code", error);
        }
    }

    @Override
    public void completeSucceeded(String actorScope, String idempotencyKey,
                                  Fingerprint fingerprint, UUID reservationId,
                                  long inventoryVersion, Instant completedAt) throws IllegalStateException {
        requireIdentity(actorScope, idempotencyKey, fingerprint);
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(completedAt, "completedAt");
        int updated = identity(jdbcClient.sql("""
                UPDATE vra.inventory_reservation_idempotency
                SET outcome_status = 'SUCCEEDED',
                    reservation_id = :reservationId,
                    inventory_version = :inventoryVersion,
                    rejection_code = NULL,
                    completed_at = :completedAt
                WHERE actor_scope = :actorScope
                  AND idempotency_key = :idempotencyKey
                  AND fingerprint_version = :fingerprintVersion
                  AND request_fingerprint = :requestFingerprint
                  AND outcome_status IS NULL
                """), actorScope, idempotencyKey, fingerprint)
                .param("reservationId", reservationId)
                .param("inventoryVersion", inventoryVersion)
                .param("completedAt", completedAt.atOffset(ZoneOffset.UTC))
                .update();
        requireSingleCompletion(updated);
    }

    @Override
    public void completeRejected(String actorScope, String idempotencyKey,
                                 Fingerprint fingerprint, ReservationFailureCode rejectionCode,
                                 Instant completedAt) throws IllegalStateException {
        requireIdentity(actorScope, idempotencyKey, fingerprint);
        Objects.requireNonNull(rejectionCode, "rejectionCode");
        Objects.requireNonNull(completedAt, "completedAt");
        int updated = identity(jdbcClient.sql("""
                UPDATE vra.inventory_reservation_idempotency
                SET outcome_status = 'REJECTED',
                    reservation_id = NULL,
                    inventory_version = NULL,
                    rejection_code = :rejectionCode,
                    completed_at = :completedAt
                WHERE actor_scope = :actorScope
                  AND idempotency_key = :idempotencyKey
                  AND fingerprint_version = :fingerprintVersion
                  AND request_fingerprint = :requestFingerprint
                  AND outcome_status IS NULL
                """), actorScope, idempotencyKey, fingerprint)
                .param("rejectionCode", rejectionCode.name())
                .param("completedAt", completedAt.atOffset(ZoneOffset.UTC))
                .update();
        requireSingleCompletion(updated);
    }

    private static void requireIdentity(String actorScope, String idempotencyKey,
                                        Fingerprint fingerprint) {
        Objects.requireNonNull(actorScope, "actorScope");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    private static JdbcClient.StatementSpec identity(JdbcClient.StatementSpec statement,
                                                     String actorScope, String idempotencyKey,
                                                     Fingerprint fingerprint) {
        return statement.param("actorScope", actorScope)
                .param("idempotencyKey", idempotencyKey)
                .param("fingerprintVersion", fingerprint.version())
                .param("requestFingerprint", fingerprint.value());
    }

    private static void requireSingleCompletion(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Expected one idempotency completion, updated " + updated);
        }
    }

    private record StoredOutcome(short version, String fingerprint, String status,
                                 UUID reservationId, Long inventoryVersion, String rejectionCode) {}
}
