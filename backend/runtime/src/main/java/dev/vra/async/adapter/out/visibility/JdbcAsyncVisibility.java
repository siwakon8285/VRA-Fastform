package dev.vra.async.adapter.out.visibility;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Restricted POC diagnostics; construct with the vra_async_observer datasource. */
public final class JdbcAsyncVisibility {
    public record StateCount(String targetCode, String state, long count) {}
    public record OutstandingAge(String targetCode, long count, long oldestAgeMillis) {}
    public record Activity(String targetCode, String action, long count) {}
    public record DeliveryHistory(long historyId, UUID eventId, String action, String fromState,
            String toState, long lifetimeAttempt, int automaticCycle,
            int cycleClaimCount, int cycleClaimLimit, String failureClass, String reasonCode) {}
    public record ReconciliationHistory(long historyId, UUID caseId, String action, String fromState,
            String toState, long lifetimeAttempt, int automaticCycle,
            int cycleClaimCount, int cycleClaimLimit, String observation, String reasonCode) {}

    private final JdbcClient jdbc;

    public JdbcAsyncVisibility(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        if (!"vra_async_observer".equals(jdbc.sql("SELECT current_user").query(String.class).single())) {
            throw new IllegalStateException("Async visibility requires the observer identity");
        }
    }

    public List<StateCount> deliveryBacklog() {
        return jdbc.sql("SELECT target_code,state,count(*) AS n FROM vra.outbox_delivery "
                        + "WHERE state IN ('READY','PROCESSING','RETRY_WAIT','FAILED',"
                        + "'RECONCILIATION_REQUIRED') GROUP BY target_code,state ORDER BY target_code,state")
                .query((row, number) -> new StateCount(row.getString(1), row.getString(2), row.getLong(3)))
                .list();
    }

    public List<StateCount> reconciliationBacklog() {
        return jdbc.sql("SELECT d.target_code,c.state,count(*) AS n FROM vra.reconciliation_case c "
                        + "JOIN vra.outbox_delivery d USING(event_id) "
                        + "WHERE c.state IN ('PENDING','CHECKING','WAITING','OPERATOR_REQUIRED') "
                        + "GROUP BY d.target_code,c.state ORDER BY d.target_code,c.state")
                .query((row, number) -> new StateCount(row.getString(1), row.getString(2), row.getLong(3)))
                .list();
    }

    /** Includes READY, PROCESSING, RETRY_WAIT, FAILED and RECONCILIATION_REQUIRED. */
    public List<OutstandingAge> oldestOutstandingAge() {
        return jdbc.sql("SELECT target_code,count(*) AS n,GREATEST(0,FLOOR(EXTRACT(EPOCH FROM "
                        + "(pg_catalog.statement_timestamp()-min(created_at)))*1000))::bigint AS age_ms "
                        + "FROM vra.outbox_delivery WHERE state NOT IN ('SUCCEEDED','CLOSED') "
                        + "GROUP BY target_code ORDER BY target_code")
                .query((row, number) -> new OutstandingAge(row.getString(1), row.getLong(2),
                        row.getLong(3))).list();
    }

    public List<Activity> deliveryActivity() {
        return jdbc.sql("SELECT d.target_code,h.action_code,count(*) AS n "
                        + "FROM vra.outbox_delivery_history h JOIN vra.outbox_delivery d USING(event_id) "
                        + "GROUP BY d.target_code,h.action_code ORDER BY d.target_code,h.action_code")
                .query((row, number) -> new Activity(row.getString(1), row.getString(2), row.getLong(3)))
                .list();
    }

    public List<Activity> reconciliationActivity() {
        return jdbc.sql("SELECT d.target_code,h.action_code,count(*) AS n "
                        + "FROM vra.reconciliation_history h JOIN vra.reconciliation_case c USING(case_id) "
                        + "JOIN vra.outbox_delivery d USING(event_id) "
                        + "GROUP BY d.target_code,h.action_code ORDER BY d.target_code,h.action_code")
                .query((row, number) -> new Activity(row.getString(1), row.getString(2), row.getLong(3)))
                .list();
    }

    public List<DeliveryHistory> deliveryHistory(UUID eventId) {
        return jdbc.sql("SELECT history_id,event_id,action_code,from_state,to_state,"
                        + "lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,"
                        + "failure_class,reason_code FROM vra.outbox_delivery_history "
                        + "WHERE event_id=:id ORDER BY history_id")
                .param("id", Objects.requireNonNull(eventId, "eventId"))
                .query((row, number) -> new DeliveryHistory(row.getLong(1), row.getObject(2, UUID.class),
                        row.getString(3), row.getString(4), row.getString(5),
                        row.getLong(6), row.getInt(7), row.getInt(8),
                        row.getInt(9), row.getString(10), row.getString(11))).list();
    }

    public List<ReconciliationHistory> reconciliationHistory(UUID caseId) {
        return jdbc.sql("SELECT history_id,case_id,action_code,from_state,to_state,"
                        + "lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,"
                        + "observation,reason_code FROM vra.reconciliation_history "
                        + "WHERE case_id=:id ORDER BY history_id")
                .param("id", Objects.requireNonNull(caseId, "caseId"))
                .query((row, number) -> new ReconciliationHistory(row.getLong(1),
                        row.getObject(2, UUID.class), row.getString(3), row.getString(4),
                        row.getString(5), row.getLong(6),
                        row.getInt(7), row.getInt(8), row.getInt(9), row.getString(10),
                        row.getString(11))).list();
    }
}
