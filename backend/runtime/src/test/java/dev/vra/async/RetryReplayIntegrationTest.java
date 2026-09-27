package dev.vra.async;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import dev.vra.async.adapter.out.control.JdbcAsyncControlRepository;
import dev.vra.async.adapter.out.delivery.JdbcDeliveryRepository;
import dev.vra.async.application.control.AsyncControlPort;
import dev.vra.async.application.delivery.DeliveryFailureClassifier;
import dev.vra.async.application.delivery.DeliveryFailureClassifier.Classification;
import dev.vra.async.application.delivery.DeliveryFailureClassifier.Evidence;
import dev.vra.async.application.delivery.DeliveryPort;
import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.application.delivery.DeliveryRetryPolicy;
import dev.vra.async.application.delivery.DeliveryService;
import dev.vra.async.bootstrap.AsyncProperties;
import dev.vra.async.contract.ReservationCreatedEventV1;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.migration.MigrationRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class RetryReplayIntegrationTest {
    private static final String PASSWORD = "stage-e-disposable-test-only";
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("vra_poc01").withUsername("postgres").withPassword(PASSWORD);
    private static AnnotationConfigApplicationContext workerContext;
    private static AnnotationConfigApplicationContext operatorContext;
    private static DeliveryService delivery;
    private static DeliveryPort workerPort;
    private static AsyncControlPort operator;
    private static final DeliveryFailureClassifier CLASSIFIER = new DeliveryFailureClassifier();

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.start();
        try {
            bootstrap("validation/poc-01/db/bootstrap.sql");
            bootstrap("validation/poc-03/db/bootstrap-async-roles.sql");
            assertEquals(3, new MigrationRunner().migrate(POSTGRES.getJdbcUrl(), "vra_migrator", PASSWORD));
            workerContext = new AnnotationConfigApplicationContext(WorkerConfiguration.class);
            operatorContext = new AnnotationConfigApplicationContext(OperatorConfiguration.class);
            delivery = workerContext.getBean(DeliveryService.class);
            workerPort = workerContext.getBean(DeliveryPort.class);
            operator = operatorContext.getBean(AsyncControlPort.class);
        } catch (Exception failure) {
            if (workerContext != null) workerContext.close();
            POSTGRES.stop();
            throw failure;
        }
    }

    @AfterAll
    static void stop() {
        if (operatorContext != null) operatorContext.close();
        if (workerContext != null) workerContext.close();
        POSTGRES.stop();
    }

    @BeforeEach
    void clearAsyncRows() throws SQLException {
        owner("TRUNCATE vra.consumer_inbox,vra.reservation_projection,vra.outbox_event CASCADE");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class WorkerConfiguration {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "vra_outbox_worker", PASSWORD);
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new JdbcTransactionManager(source);
        }
        @Bean DeliveryPort deliveryPort(DataSource source) {
            return new JdbcDeliveryRepository(JdbcClient.create(source));
        }
        @Bean DeliveryService deliveryService(DeliveryPort port) {
            return new DeliveryService(port);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class OperatorConfiguration {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "vra_async_operator", PASSWORD);
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new JdbcTransactionManager(source);
        }
        @Bean AsyncControlPort controlPort(DataSource source) {
            return new JdbcAsyncControlRepository(JdbcClient.create(source));
        }
    }

    @Test
    void durableTransientCycleExhaustsThenReplaysSameEventIntoNewCycle() throws Exception {
        Fixture fixture = fixture(TargetCode.RESERVATION_PROJECTION);
        String eventBefore = eventRow(fixture);
        String reservationBefore = reservationRow(fixture);
        Classification transientFailure = CLASSIFIER.classify(Evidence.ROLLED_BACK_CONSUMER_TRANSIENT);
        AsyncProperties tuning = new AsyncProperties();
        tuning.setRetryBaseDelay(Duration.ofSeconds(10));
        tuning.setRetryMaxDelay(Duration.ofSeconds(10));
        DeliveryRetryPolicy policy = new DeliveryRetryPolicy(tuning.getRetryBaseDelay(),
                tuning.getRetryMaxDelay(), tuning.getRetryJitterFraction(), () -> 0);

        for (int attempt = 1; attempt <= 5; attempt++) {
            ClaimedDelivery claim = claim(fixture, TargetCode.RESERVATION_PROJECTION);
            assertEquals(1, claim.automaticCycle());
            assertEquals(attempt, claim.cycleClaimCount());
            assertEquals(attempt, claim.deliveryAttemptCount());
            assertEquals(5, claim.cycleClaimLimit());
            if (attempt < 5) {
                Duration delay = policy.nextDelay(claim, transientFailure).orElseThrow();
                assertEquals("RETRY_WAIT", delivery.retryTransient(claim, delay));
                assertEquals("RETRY_WAIT:RETRYABLE_TRANSIENT:RETRYABLE_TRANSIENT:1:" + attempt + ":5",
                        value("SELECT state||':'||failure_class||':'||reason_code||':'||automatic_cycle||':'||"
                                + "cycle_claim_count||':'||cycle_claim_limit FROM vra.outbox_delivery WHERE event_id='"
                                + fixture.eventId + "'"));
                assertEquals("t", value("SELECT claim_token IS NULL AND next_eligible_at > statement_timestamp() "
                        + "FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId + "'"));
                String dueBefore = value("SELECT next_eligible_at::text FROM vra.outbox_delivery WHERE event_id='"
                        + fixture.eventId + "'");
                assertTrue(delivery.claim(TargetCode.RESERVATION_PROJECTION, "before-due", 1,
                        Duration.ofSeconds(30)).isEmpty());
                if (attempt == 2) {
                    tuning.setRetryBaseDelay(Duration.ofSeconds(20));
                    tuning.setRetryMaxDelay(Duration.ofSeconds(20));
                    policy = new DeliveryRetryPolicy(tuning.getRetryBaseDelay(),
                            tuning.getRetryMaxDelay(), tuning.getRetryJitterFraction(), () -> 0);
                    try (AnnotationConfigApplicationContext restarted = new AnnotationConfigApplicationContext(
                            WorkerConfiguration.class)) {
                        assertTrue(restarted.getBean(DeliveryService.class)
                                .claim(TargetCode.RESERVATION_PROJECTION, "recreated", 1,
                                        Duration.ofSeconds(30)).isEmpty());
                    }
                    assertEquals(dueBefore, value("SELECT next_eligible_at::text FROM vra.outbox_delivery "
                            + "WHERE event_id='" + fixture.eventId + "'"));
                    assertEquals("1:2:5", value("SELECT automatic_cycle||':'||cycle_claim_count||':'||"
                            + "cycle_claim_limit FROM vra.outbox_delivery WHERE event_id='"
                            + fixture.eventId + "'"));
                }
                owner("UPDATE vra.outbox_delivery SET next_eligible_at=statement_timestamp()-interval '1 second' "
                        + "WHERE event_id='" + fixture.eventId + "'");
                assertEquals("t", value("SELECT next_eligible_at < statement_timestamp() "
                        + "FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId + "'"));
            } else {
                assertTrue(policy.nextDelay(claim, transientFailure).isEmpty());
                assertEquals("FAILED", delivery.retryTransient(claim, Duration.ofMillis(1)));
            }
        }
        assertEquals("FAILED:RETRYABLE_TRANSIENT:RETRY_EXHAUSTED:1:5:5:5",
                value("SELECT state||':'||failure_class||':'||reason_code||':'||automatic_cycle||':'||"
                        + "cycle_claim_count||':'||cycle_claim_limit||':'||delivery_attempt_count "
                        + "FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId + "'"));
        assertEquals("t", value("SELECT next_eligible_at IS NULL AND claim_token IS NULL "
                + "FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId + "'"));
        assertEquals(4, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code='RETRY_SCHEDULED'"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code='FAILED' AND failure_class='RETRYABLE_TRANSIENT' "
                + "AND reason_code='RETRY_EXHAUSTED' AND automatic_cycle=1 AND cycle_claim_count=5"));
        assertTrue(delivery.claim(TargetCode.RESERVATION_PROJECTION, "sixth", 1,
                Duration.ofSeconds(30)).isEmpty());
        String historyBefore = value("SELECT json_agg(row_to_json(h) ORDER BY history_id)::text "
                + "FROM vra.outbox_delivery_history h WHERE event_id='" + fixture.eventId + "'");
        assertTrue(operator.replay(fixture.eventId, "reviewer-e-1"));
        assertEquals("READY:2:0:5:5:true", value("SELECT state||':'||automatic_cycle||':'||cycle_claim_count||':'||"
                + "cycle_claim_limit||':'||delivery_attempt_count||':'||"
                + "(failure_class IS NULL AND reason_code IS NULL) FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(historyBefore, value("SELECT json_agg(row_to_json(h) ORDER BY history_id)::text "
                + "FROM vra.outbox_delivery_history h WHERE event_id='" + fixture.eventId
                + "' AND automatic_cycle=1"));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code='CONTROL_REPLAY' AND from_state='FAILED' AND to_state='READY' "
                + "AND actor_kind='ASYNC_OPERATOR' AND actor_ref='reviewer-e-1' "
                + "AND reason_code='CONTROLLED_REPLAY' AND automatic_cycle=2 AND cycle_claim_count=0 "
                + "AND cycle_claim_limit=5 AND lifetime_attempt=5"));
        assertEquals(eventBefore, eventRow(fixture));
        assertEquals(reservationBefore, reservationRow(fixture));
        assertEquals(1, count("SELECT count(*) FROM vra.outbox_event WHERE reservation_id='"
                + fixture.reservationId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.reservation_projection WHERE reservation_id='"
                + fixture.reservationId + "'"));
        ClaimedDelivery secondCycle = claim(fixture, TargetCode.RESERVATION_PROJECTION);
        assertEquals(2, secondCycle.automaticCycle());
        assertEquals(1, secondCycle.cycleClaimCount());
        assertEquals(5, secondCycle.cycleClaimLimit());
        assertEquals(6, secondCycle.deliveryAttemptCount());
    }

    @Test
    void explicitTerminalClassesUseOnlyStableV3Pairs() throws Exception {
        for (Evidence evidence : List.of(Evidence.NON_RETRYABLE_VALIDATION,
                Evidence.UNSUPPORTED_EVENT_CONTRACT, Evidence.OPERATOR_REVIEW_REQUIRED)) {
            Fixture fixture = evidence == Evidence.UNSUPPORTED_EVENT_CONTRACT
                    ? unsupportedEventFixture() : fixture(TargetCode.RESERVATION_PROJECTION);
            ClaimedDelivery claim = claim(fixture, TargetCode.RESERVATION_PROJECTION);
            if (evidence == Evidence.UNSUPPORTED_EVENT_CONTRACT) {
                assertEquals(2, count("SELECT schema_version FROM vra.outbox_event WHERE event_id='"
                        + fixture.eventId + "'"));
                assertThrows(IllegalArgumentException.class, () -> readEventContract(fixture));
            }
            Classification classified = CLASSIFIER.classify(evidence);
            assertTrue(workerPort.fail(claim.eventId(), claim.claimToken(),
                    classified.failureClass().name(), classified.reasonCode()));
            assertEquals("FAILED:" + classified.failureClass() + ":" + classified.reasonCode(),
                    value("SELECT state||':'||failure_class||':'||reason_code FROM vra.outbox_delivery WHERE event_id='"
                            + fixture.eventId + "'"));
            assertEquals(1, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                    + fixture.eventId + "' AND action_code='FAILED' AND failure_class='"
                    + classified.failureClass() + "' AND reason_code='" + classified.reasonCode() + "'"));
            assertEquals(0, count("SELECT count(*) FROM vra.consumer_inbox WHERE event_id='"
                    + fixture.eventId + "'"));
        }
    }

    @Test
    void unknownIsNotAnOrdinaryRetryOrFailClassification() throws Exception {
        Fixture fixture = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        ClaimedDelivery claim = claim(fixture, TargetCode.VALIDATION_EXTERNAL_EFFECT);
        Classification unknown = CLASSIFIER.classify(Evidence.EXTERNAL_OUTCOME_UNCERTAIN);
        AsyncProperties tuning = new AsyncProperties();
        DeliveryRetryPolicy policy = new DeliveryRetryPolicy(tuning.getRetryBaseDelay(),
                tuning.getRetryMaxDelay(), tuning.getRetryJitterFraction(), () -> 0);
        assertThrows(IllegalStateException.class, () -> policy.nextDelay(claim, unknown));
        try (Connection worker = connection("vra_outbox_worker"); Statement statement = worker.createStatement()) {
            SQLException rejected = assertThrows(SQLException.class, () -> statement.execute(
                    "SELECT vra.async_fail_delivery('" + fixture.eventId + "','" + claim.claimToken()
                            + "','UNKNOWN_OUTCOME','UNKNOWN_EXTERNAL_RESULT')"));
            assertEquals("22023", rejected.getSQLState());
        }
        assertEquals("PROCESSING", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code IN ('RETRY_SCHEDULED','FAILED')"));
        assertNotNull(delivery.handoffUnknown(claim));
        assertEquals("RECONCILIATION_REQUIRED:UNKNOWN_OUTCOME", value("SELECT state||':'||failure_class "
                + "FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId + "'"));
    }

    @Test
    void replayRejectsSucceededClosedReconciliationRequiredUnknownAndActiveCase() throws Exception {
        Fixture succeeded = fixture(TargetCode.RESERVATION_PROJECTION);
        owner("UPDATE vra.outbox_delivery SET state='SUCCEEDED' WHERE event_id='" + succeeded.eventId + "'");
        assertReplayDenied(succeeded);

        Fixture closed = fixture(TargetCode.RESERVATION_PROJECTION);
        owner("UPDATE vra.outbox_delivery SET state='CLOSED',reason_code='CONTROLLED_CLOSE' "
                + "WHERE event_id='" + closed.eventId + "'");
        assertReplayDenied(closed);

        Fixture pending = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        ClaimedDelivery pendingClaim = claim(pending, TargetCode.VALIDATION_EXTERNAL_EFFECT);
        UUID pendingCase = delivery.handoffUnknown(pendingClaim);
        assertNotNull(pendingCase);
        assertReplayDenied(pending);

        Fixture unknown = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        owner("UPDATE vra.outbox_delivery SET state='FAILED',failure_class='UNKNOWN_OUTCOME',"
                + "reason_code='RECONCILIATION_EXHAUSTED' WHERE event_id='" + unknown.eventId + "'");
        assertReplayDenied(unknown);

        ownerCompound("UPDATE vra.reconciliation_case SET state='OPERATOR_REQUIRED',next_eligible_at=NULL "
                        + "WHERE case_id='" + pendingCase + "'",
                "UPDATE vra.outbox_delivery SET state='FAILED',failure_class='OPERATOR_REQUIRED',"
                        + "reason_code='OPERATOR_REQUIRED' WHERE event_id='" + pending.eventId + "'");
        assertEquals("OPERATOR_REQUIRED", value("SELECT state FROM vra.reconciliation_case WHERE case_id='"
                + pendingCase + "'"));
        assertReplayDenied(pending); // active-case guard, even with a non-UNKNOWN delivery class
    }

    @Test
    void replayGuardRejectsTransientMalformedActivePairsBeforeDeferredCommit() throws Exception {
        for (String state : List.of("PENDING", "CHECKING", "WAITING")) {
            Fixture fixture = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
            UUID caseId = UUID.randomUUID();
            try (Connection admin = connection("postgres"); Statement statement = admin.createStatement()) {
                admin.setAutoCommit(false);
                statement.execute("SET ROLE vra_owner");
                statement.execute("UPDATE vra.outbox_delivery SET state='FAILED',"
                        + "failure_class='OPERATOR_REQUIRED',reason_code='OPERATOR_REQUIRED' WHERE event_id='"
                        + fixture.eventId + "'");
                String nextEligible = state.equals("CHECKING") ? "NULL" : "statement_timestamp()";
                String token = state.equals("CHECKING") ? "'" + UUID.randomUUID() + "'" : "NULL";
                int count = state.equals("PENDING") ? 0 : 1;
                statement.execute("INSERT INTO vra.reconciliation_case(case_id,event_id,state,external_knowledge,"
                        + "reason_code,next_eligible_at,claim_token,claim_until,lifetime_attempt_count,"
                        + "automatic_cycle,cycle_claim_count,cycle_claim_limit,created_at,state_changed_at) VALUES ('"
                        + caseId + "','" + fixture.eventId + "','" + state + "','UNKNOWN','OPERATOR_REQUIRED',"
                        + nextEligible + "," + token + ","
                        + (state.equals("CHECKING") ? "statement_timestamp()+interval '30 seconds'" : "NULL")
                        + "," + count + ",1," + count + ",4,statement_timestamp(),statement_timestamp())");
                statement.execute("SET ROLE vra_async_operator");
                assertEquals("f", scalar(admin, "SELECT vra.async_control_replay('" + fixture.eventId
                        + "','CONTROLLED_REPLAY','transient-pair-review')"));
                assertEquals("FAILED", scalar(admin, "SELECT state FROM vra.outbox_delivery WHERE event_id='"
                        + fixture.eventId + "'"));
                assertEquals("0", scalar(admin, "SELECT count(*) FROM vra.outbox_delivery_history "
                        + "WHERE event_id='" + fixture.eventId + "' AND action_code='CONTROL_REPLAY'"));
                admin.rollback(); // these pair states cannot commit alongside FAILED
            }
            assertEquals("READY", value("SELECT state FROM vra.outbox_delivery WHERE event_id='"
                    + fixture.eventId + "'"));
            assertEquals(0, count("SELECT count(*) FROM vra.reconciliation_case WHERE event_id='"
                    + fixture.eventId + "'"));
        }
    }

    @Test
    void workerAndOperatorHaveOnlyTheirIntendedCapabilities() throws Exception {
        Fixture fixture = fixture(TargetCode.RESERVATION_PROJECTION);
        ClaimedDelivery claim = claim(fixture, TargetCode.RESERVATION_PROJECTION); // worker claim allowed
        assertEquals("RETRY_WAIT", delivery.retryTransient(claim, Duration.ofSeconds(10))); // worker retry allowed
        String before = deliveryRow(fixture);
        for (String sql : List.of(
                "SELECT vra.async_control_replay('" + fixture.eventId + "','CONTROLLED_REPLAY','worker')",
                "SELECT vra.async_control_resume(gen_random_uuid(),'CONTROLLED_RESUME','worker')",
                "SELECT vra.async_control_close('" + fixture.eventId + "','CONTROLLED_CLOSE','worker')",
                "UPDATE vra.outbox_delivery SET state='FAILED' WHERE event_id='" + fixture.eventId + "'",
                "UPDATE vra.outbox_delivery SET cycle_claim_limit=16 WHERE event_id='" + fixture.eventId + "'",
                "SET ROLE vra_owner", "SET ROLE vra_async_executor")) {
            denied("vra_outbox_worker", sql);
        }
        assertEquals(before, deliveryRow(fixture));
        for (String sql : List.of(
                "SELECT * FROM vra.async_claim_delivery('RESERVATION_PROJECTION','operator',1,1000::bigint)",
                "SELECT vra.async_retry_delivery('" + fixture.eventId + "','" + claim.claimToken()
                        + "','RETRYABLE_TRANSIENT',1000::bigint)",
                "SELECT vra.async_fail_delivery('" + fixture.eventId + "','" + claim.claimToken()
                        + "','NON_RETRYABLE','NON_RETRYABLE')",
                "UPDATE vra.outbox_delivery SET state='FAILED' WHERE event_id='" + fixture.eventId + "'",
                "UPDATE vra.inventory_balance SET reserved=reserved",
                "SET ROLE vra_owner", "SET ROLE vra_async_executor")) {
            denied("vra_async_operator", sql);
        }
        assertEquals(before, deliveryRow(fixture));
    }

    @Test
    void reclaimedExternalClaimStillCannotBypassUnknownHandoff() throws Exception {
        Fixture fixture = fixture(TargetCode.VALIDATION_EXTERNAL_EFFECT);
        ClaimedDelivery old = claim(fixture, TargetCode.VALIDATION_EXTERNAL_EFFECT);
        owner("UPDATE vra.outbox_delivery SET claim_until=statement_timestamp()-interval '1 second' "
                + "WHERE event_id='" + fixture.eventId + "'");
        ClaimedDelivery current = claim(fixture, TargetCode.VALIDATION_EXTERNAL_EFFECT);
        assertTrue(current.reclaimed());
        assertNotEquals(old.claimToken(), current.claimToken());
        Classification transientFailure = CLASSIFIER.classify(Evidence.ROLLED_BACK_CONSUMER_TRANSIENT);
        AsyncProperties tuning = new AsyncProperties();
        DeliveryRetryPolicy policy = new DeliveryRetryPolicy(tuning.getRetryBaseDelay(),
                tuning.getRetryMaxDelay(), tuning.getRetryJitterFraction(), () -> 0);
        assertThrows(IllegalStateException.class, () -> policy.nextDelay(current, transientFailure));
        String before = deliveryRow(fixture);
        int histories = count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "'");
        assertThrows(IllegalStateException.class, () -> delivery.complete(current));
        assertThrows(IllegalStateException.class, () -> delivery.retryTransient(current, Duration.ofMillis(1)));
        assertThrows(IllegalStateException.class, () -> delivery.failNonRetryable(current));
        assertThrows(IllegalStateException.class, () -> delivery.handoffUnknown(current));
        assertEquals(before, deliveryRow(fixture));
        assertEquals(histories, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "'"));
        assertNotNull(delivery.handoffReclaimedExternal(current));
        assertEquals("RECONCILIATION_REQUIRED:UNKNOWN_OUTCOME", value("SELECT state||':'||failure_class "
                + "FROM vra.outbox_delivery WHERE event_id='" + fixture.eventId + "'"));
    }

    private static void assertReplayDenied(Fixture fixture) throws SQLException {
        String deliveryBefore = deliveryRow(fixture);
        String eventBefore = eventRow(fixture);
        int histories = count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "'");
        assertFalse(operator.replay(fixture.eventId, "reviewer-denied"));
        assertEquals(deliveryBefore, deliveryRow(fixture));
        assertEquals(eventBefore, eventRow(fixture));
        assertEquals(histories, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "'"));
        assertEquals(0, count("SELECT count(*) FROM vra.outbox_delivery_history WHERE event_id='"
                + fixture.eventId + "' AND action_code='CONTROL_REPLAY'"));
    }

    private static ClaimedDelivery claim(Fixture fixture, TargetCode target) {
        List<ClaimedDelivery> claims = delivery.claim(target, "worker-e", 1, Duration.ofSeconds(30));
        assertEquals(1, claims.size());
        assertEquals(fixture.eventId, claims.getFirst().eventId());
        return claims.getFirst();
    }

    private static Fixture fixture(TargetCode target) throws SQLException {
        Fixture fixture = seedReservation();
        try (Connection runtime = connection("vra_runtime"); PreparedStatement statement = runtime.prepareStatement(
                "SELECT vra.async_publish_reservation(?::uuid,?::uuid,?::varchar)")) {
            statement.setObject(1, fixture.eventId);
            statement.setObject(2, fixture.reservationId);
            statement.setString(3, target.name());
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(fixture.eventId, rows.getObject(1, UUID.class));
            }
        }
        return fixture;
    }

    private static Fixture unsupportedEventFixture() throws SQLException {
        Fixture fixture = seedReservation();
        ownerCompound("INSERT INTO vra.outbox_event(event_id,event_type,schema_version,occurred_at,reservation_id,"
                        + "sku_id,owner_id,location_id,stock_status,quantity) SELECT '" + fixture.eventId
                        + "','inventory.reservation.created',2,r.created_at,r.reservation_id,r.sku_id,r.owner_id,"
                        + "r.location_id,r.stock_status,r.quantity FROM vra.inventory_reservation r WHERE r.reservation_id='"
                        + fixture.reservationId + "'",
                "INSERT INTO vra.outbox_delivery(event_id,target_code,state,automatic_cycle,cycle_claim_count,"
                        + "cycle_claim_limit,created_at,state_changed_at) VALUES ('" + fixture.eventId
                        + "','RESERVATION_PROJECTION','READY',1,0,5,statement_timestamp(),statement_timestamp())",
                "INSERT INTO vra.outbox_delivery_history(event_id,action_code,to_state,lifetime_attempt,"
                        + "automatic_cycle,cycle_claim_count,cycle_claim_limit,actor_kind,recorded_at) VALUES ('"
                        + fixture.eventId + "','CREATED','READY',0,1,0,5,'RUNTIME',statement_timestamp())");
        return fixture;
    }

    private static ReservationCreatedEventV1 readEventContract(Fixture fixture) throws SQLException {
        try (Connection worker = connection("vra_outbox_worker"); PreparedStatement statement = worker.prepareStatement(
                "SELECT event_id,event_type,schema_version,occurred_at,reservation_id,sku_id,owner_id,"
                        + "location_id,stock_status,quantity FROM vra.outbox_event WHERE event_id=?")) {
            statement.setObject(1, fixture.eventId);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                return new ReservationCreatedEventV1(row.getObject("event_id", UUID.class),
                        row.getString("event_type"), row.getInt("schema_version"),
                        row.getTimestamp("occurred_at").toInstant(), new Payload(
                        row.getObject("reservation_id", UUID.class), row.getObject("sku_id", UUID.class),
                        row.getObject("owner_id", UUID.class), row.getObject("location_id", UUID.class),
                        StockStatus.valueOf(row.getString("stock_status")), row.getLong("quantity")));
            }
        }
    }

    private static Fixture seedReservation() throws SQLException {
        Fixture fixture = new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        owner("INSERT INTO vra.inventory_balance VALUES ('" + fixture.skuId + "','" + fixture.ownerId
                + "','" + fixture.locationId + "','AVAILABLE',10,1,1)");
        owner("INSERT INTO vra.inventory_reservation VALUES ('" + fixture.reservationId + "','"
                + fixture.skuId + "','" + fixture.ownerId + "','" + fixture.locationId
                + "','AVAILABLE',1,statement_timestamp())");
        return fixture;
    }

    private static String deliveryRow(Fixture fixture) throws SQLException {
        return value("SELECT row_to_json(d)::text FROM vra.outbox_delivery d WHERE event_id='"
                + fixture.eventId + "'");
    }

    private static String eventRow(Fixture fixture) throws SQLException {
        return value("SELECT row_to_json(e)::text FROM vra.outbox_event e WHERE event_id='"
                + fixture.eventId + "'");
    }

    private static String reservationRow(Fixture fixture) throws SQLException {
        return value("SELECT row_to_json(r)::text FROM vra.inventory_reservation r WHERE reservation_id='"
                + fixture.reservationId + "'");
    }

    private static void denied(String role, String sql) throws SQLException {
        try (Connection connection = connection(role); Statement statement = connection.createStatement()) {
            SQLException error = assertThrows(SQLException.class, () -> statement.execute(sql), sql);
            assertEquals("42501", error.getSQLState(), sql);
        }
    }

    private static int count(String sql) throws SQLException {
        return Integer.parseInt(value(sql));
    }

    private static String value(String sql) throws SQLException {
        try (Connection admin = connection("postgres")) {
            return scalar(admin, sql);
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            String result = rows.getString(1);
            assertFalse(rows.next(), sql);
            return result;
        }
    }

    private static void owner(String sql) throws SQLException {
        ownerCompound(sql);
    }

    private static void ownerCompound(String... commands) throws SQLException {
        try (Connection admin = connection("postgres"); Statement statement = admin.createStatement()) {
            admin.setAutoCommit(false);
            statement.execute("SET ROLE vra_owner");
            for (String command : commands) statement.execute(command);
            admin.commit();
        }
    }

    private static Connection connection(String role) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), role, PASSWORD);
    }

    private static void bootstrap(String script) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("validation/poc-01/db"))) {
            root = root.getParent();
            if (root == null) throw new IllegalStateException("Repository root missing");
        }
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(root.resolve(script)), "/tmp/stage-e-bootstrap.sql");
        var result = POSTGRES.execInContainer("psql", "-U", "postgres", "-d", "vra_poc01",
                "-v", "ON_ERROR_STOP=1", "-v", "migrator_password=" + PASSWORD,
                "-v", "runtime_password=" + PASSWORD, "-v", "outbox_worker_password=" + PASSWORD,
                "-v", "reconciliation_worker_password=" + PASSWORD,
                "-v", "async_operator_password=" + PASSWORD,
                "-v", "projection_rebuilder_password=" + PASSWORD,
                "-v", "async_observer_password=" + PASSWORD,
                "-f", "/tmp/stage-e-bootstrap.sql");
        assertEquals(0, result.getExitCode(), result.getStderr());
    }

    private record Fixture(UUID reservationId, UUID eventId, UUID skuId, UUID ownerId, UUID locationId) {}
}
