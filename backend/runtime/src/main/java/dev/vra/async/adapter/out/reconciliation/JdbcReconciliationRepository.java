package dev.vra.async.adapter.out.reconciliation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.vra.async.application.reconciliation.ReconciliationPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Exact V3 function calls under the reconciliation worker credential. */
public final class JdbcReconciliationRepository implements ReconciliationPort {
    private final JdbcClient jdbc;

    public JdbcReconciliationRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Claim> claim(String workerRef, int batchSize, Duration lease) {
        if (workerRef == null || workerRef.isBlank() || workerRef.length() > 128
                || batchSize < 1 || batchSize > 128) {
            throw new IllegalArgumentException("Invalid reconciliation worker or batch size");
        }
        return jdbc.sql("SELECT * FROM vra.async_claim_reconciliation(:worker,:batch,:lease)")
                .param("worker", workerRef).param("batch", batchSize).param("lease", milliseconds(lease))
                .query((row, number) -> new Claim(row.getObject("case_id", UUID.class),
                        row.getObject("event_id", UUID.class), row.getObject("claim_token", UUID.class),
                        row.getLong("lifetime_attempt_count"), row.getInt("automatic_cycle"),
                        row.getInt("cycle_claim_count"), row.getInt("cycle_claim_limit"))).list();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryCycle readDeliveryCycle(UUID eventId) {
        return jdbc.sql("SELECT automatic_cycle,cycle_claim_count,cycle_claim_limit "
                        + "FROM vra.outbox_delivery WHERE event_id=:event")
                .param("event", Objects.requireNonNull(eventId, "eventId"))
                .query((row, number) -> new DeliveryCycle(row.getInt(1), row.getInt(2), row.getInt(3)))
                .single();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String waitIndeterminate(UUID caseId, UUID claimToken, Duration delay) {
        String state = jdbc.sql("SELECT COALESCE(vra.async_wait_reconciliation("
                        + ":caseId,:token,'UNKNOWN_EXTERNAL_RESULT',:delay),'')")
                .param("caseId", Objects.requireNonNull(caseId)).param("token", Objects.requireNonNull(claimToken))
                .param("delay", milliseconds(delay)).query(String.class).single();
        return state.isEmpty() ? null : state;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean confirmSuccess(UUID caseId, UUID claimToken) {
        return jdbc.sql("SELECT vra.async_confirm_external_success(:caseId,:token)")
                .param("caseId", Objects.requireNonNull(caseId)).param("token", Objects.requireNonNull(claimToken))
                .query(Boolean.class).single();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String confirmNoEffect(UUID caseId, UUID claimToken, boolean retrySafe, Duration retryDelay) {
        String state = jdbc.sql("SELECT COALESCE(vra.async_confirm_external_no_effect("
                        + ":caseId,:token,:safe,:delay),'')")
                .param("caseId", Objects.requireNonNull(caseId)).param("token", Objects.requireNonNull(claimToken))
                .param("safe", retrySafe).param("delay", milliseconds(retryDelay))
                .query(String.class).single();
        return state.isEmpty() ? null : state;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean exhaust(UUID caseId, UUID claimToken) {
        return jdbc.sql("SELECT vra.async_exhaust_reconciliation(:caseId,:token,'RECONCILIATION_EXHAUSTED')")
                .param("caseId", Objects.requireNonNull(caseId)).param("token", Objects.requireNonNull(claimToken))
                .query(Boolean.class).single();
    }

    private static long milliseconds(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long value;
        try {
            value = duration.toMillis();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Duration overflows milliseconds", error);
        }
        if (value < 1 || value > 86_400_000) {
            throw new IllegalArgumentException("Duration must be 1..86400000 ms");
        }
        return value;
    }
}
