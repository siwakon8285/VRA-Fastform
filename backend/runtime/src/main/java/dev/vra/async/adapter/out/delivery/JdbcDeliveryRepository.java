package dev.vra.async.adapter.out.delivery;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.vra.async.application.delivery.DeliveryPort;

/** JDBC mapping for the committed V3 capability surface. */
@Repository
public class JdbcDeliveryRepository implements DeliveryPort {
    private final JdbcClient jdbc;

    public JdbcDeliveryRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<ClaimedDelivery> claim(TargetCode target, String workerRef, int batchSize, Duration lease) {
        Objects.requireNonNull(target, "target");
        if (workerRef == null || workerRef.isBlank() || workerRef.length() > 128
                || batchSize < 1 || batchSize > 128) {
            throw new IllegalArgumentException("Invalid claim worker or batch size");
        }
        long leaseMs = boundedMillis(lease);
        return jdbc.sql("SELECT * FROM vra.async_claim_delivery(:target,:worker,:batch,:lease)")
                .param("target", target.name()).param("worker", workerRef)
                .param("batch", batchSize).param("lease", leaseMs)
                .query((rs, rowNum) -> new ClaimedDelivery(
                        rs.getObject("event_id", UUID.class), rs.getObject("claim_token", UUID.class),
                        TargetCode.valueOf(rs.getString("target_code")), rs.getBoolean("reclaimed"),
                        rs.getLong("delivery_attempt_count"), rs.getInt("automatic_cycle"),
                        rs.getInt("cycle_claim_count"), rs.getInt("cycle_claim_limit")))
                .list();
    }

    @Override
    public boolean relinquish(UUID eventId, UUID claimToken) {
        return booleanOperation("SELECT vra.async_relinquish_delivery(:event,:token)", eventId, claimToken);
    }

    @Override
    public boolean complete(UUID eventId, UUID claimToken) {
        return booleanOperation("SELECT vra.async_complete_delivery(:event,:token)", eventId, claimToken);
    }

    @Override
    public String retry(UUID eventId, UUID claimToken, Duration delay) {
        String state = jdbc.sql("SELECT COALESCE(vra.async_retry_delivery(:event,:token,:reason,:delay),'')")
                .param("event", Objects.requireNonNull(eventId)).param("token", Objects.requireNonNull(claimToken))
                .param("reason", "RETRYABLE_TRANSIENT").param("delay", boundedMillis(delay))
                .query(String.class).single();
        return state.isEmpty() ? null : state;
    }

    @Override
    public boolean fail(UUID eventId, UUID claimToken, String failureClass, String reasonCode) {
        return jdbc.sql("SELECT vra.async_fail_delivery(:event,:token,:class,:reason)")
                .param("event", Objects.requireNonNull(eventId)).param("token", Objects.requireNonNull(claimToken))
                .param("class", Objects.requireNonNull(failureClass)).param("reason", Objects.requireNonNull(reasonCode))
                .query(Boolean.class).single();
    }

    @Override
    public UUID handoffUnknown(UUID eventId, UUID claimToken, String reasonCode) {
        String caseId = jdbc.sql("SELECT COALESCE(vra.async_handoff_unknown(:event,:token,:reason)::text,'')")
                .param("event", Objects.requireNonNull(eventId)).param("token", Objects.requireNonNull(claimToken))
                .param("reason", Objects.requireNonNull(reasonCode))
                .query(String.class).single();
        return caseId.isEmpty() ? null : UUID.fromString(caseId);
    }

    private boolean booleanOperation(String sql, UUID eventId, UUID claimToken) {
        return jdbc.sql(sql).param("event", Objects.requireNonNull(eventId))
                .param("token", Objects.requireNonNull(claimToken)).query(Boolean.class).single();
    }

    private static long boundedMillis(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        final long milliseconds;
        try {
            milliseconds = duration.toMillis();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Duration overflows milliseconds", error);
        }
        if (milliseconds < 1 || milliseconds > 86_400_000) {
            throw new IllegalArgumentException("Duration must be 1..86400000 ms");
        }
        return milliseconds;
    }
}
