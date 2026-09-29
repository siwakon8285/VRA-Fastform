package dev.vra.async;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import dev.vra.async.application.delivery.JitterSource;
import dev.vra.async.application.external.ExternalEffectPort.Observation;
import dev.vra.async.application.external.ExternalEffectPort;
import dev.vra.async.application.reconciliation.ReconciliationPort;
import dev.vra.async.application.reconciliation.ReconciliationPolicy;
import dev.vra.async.application.reconciliation.ReconciliationService;
import dev.vra.async.application.reconciliation.ReconciliationPort.Claim;
import dev.vra.async.application.reconciliation.ReconciliationPort.DeliveryCycle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationPolicyTest {
    private static ReconciliationPolicy policy(JitterSource jitter) {
        return new ReconciliationPolicy(Duration.ofMillis(500), Duration.ofSeconds(5),
                Duration.ofMillis(200), Duration.ofSeconds(5), 0.20, jitter);
    }

    private static Claim claim(int count, int limit) {
        return new Claim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                count, 1, count, limit);
    }

    @Test
    void persistedCaseCountControlsBoundedQueryDelay() {
        assertEquals(Duration.ofMillis(500), policy(() -> 0).nextQueryDelay(claim(1, 4)).orElseThrow());
        assertEquals(Duration.ofMillis(1000), policy(() -> 0).nextQueryDelay(claim(2, 4)).orElseThrow());
        assertEquals(Duration.ofMillis(2000), policy(() -> 0).nextQueryDelay(claim(3, 4)).orElseThrow());
        assertTrue(policy(() -> 0).nextQueryDelay(claim(4, 4)).isEmpty());
        assertEquals(Duration.ofMillis(400), policy(() -> -1).nextQueryDelay(claim(1, 4)).orElseThrow());
        assertEquals(Duration.ofMillis(600), policy(() -> 1).nextQueryDelay(claim(1, 4)).orElseThrow());
        assertEquals(Duration.ofMillis(5000), policy(() -> 0)
                .nextQueryDelay(claim(15, 16)).orElseThrow());
    }

    @Test
    void noEffectRetryUsesPersistedDeliveryCycleAndSameBoundedFormula() {
        assertEquals(Duration.ofMillis(200), policy(() -> 0)
                .noEffectRetryDelay(new DeliveryCycle(1, 1, 5)).orElseThrow());
        assertEquals(Duration.ofMillis(1600), policy(() -> 0)
                .noEffectRetryDelay(new DeliveryCycle(1, 4, 5)).orElseThrow());
        assertTrue(policy(() -> 0).noEffectRetryDelay(new DeliveryCycle(1, 5, 5)).isEmpty());
    }

    @Test
    void timingValidationAndMillisecondClampStayWithinV3() {
        assertEquals(Duration.ofMillis(1), new ReconciliationPolicy(Duration.ofMillis(1),
                Duration.ofMillis(1), Duration.ofMillis(1), Duration.ofMillis(1), 0.5, () -> -1)
                .nextQueryDelay(claim(1, 4)).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> policy(() -> 1.1)
                .nextQueryDelay(claim(1, 4)));
        assertThrows(IllegalArgumentException.class, () -> new ReconciliationPolicy(
                Duration.ofDays(1000000), Duration.ofDays(1000000), Duration.ofMillis(1),
                Duration.ofMillis(1), 0, () -> 0));
    }

    @Test
    void observationDecisionsAreExact() {
        assertEquals(ReconciliationPolicy.Decision.CONFIRM_SUCCESS,
                policy(() -> 0).decide(Observation.CONFIRMED_SUCCEEDED));
        assertEquals(ReconciliationPolicy.Decision.CONFIRM_NO_EFFECT,
                policy(() -> 0).decide(Observation.CONFIRMED_NO_EFFECT));
        assertEquals(ReconciliationPolicy.Decision.WAIT_INDETERMINATE,
                policy(() -> 0).decide(Observation.INDETERMINATE));
    }

    @Test
    void observationExceptionOnlySchedulesAnIndeterminateWait() {
        Claim current = claim(1, 4);
        class RecordingPort implements ReconciliationPort {
            int waits;
            @Override public List<Claim> claim(String worker, int batch, Duration lease) {
                return List.of(current);
            }
            @Override public DeliveryCycle readDeliveryCycle(UUID eventId) {
                throw new AssertionError("No delivery retry decision is permitted");
            }
            @Override public String waitIndeterminate(UUID caseId, UUID token, Duration delay) {
                assertEquals(current.caseId(), caseId);
                assertEquals(current.claimToken(), token);
                assertEquals(Duration.ofMillis(500), delay);
                waits++;
                return "WAITING";
            }
            @Override public boolean confirmSuccess(UUID caseId, UUID token) {
                throw new AssertionError("No confirmed success is permitted");
            }
            @Override public String confirmNoEffect(UUID caseId, UUID token, boolean safe, Duration delay) {
                throw new AssertionError("No confirmed absence is permitted");
            }
            @Override public boolean exhaust(UUID caseId, UUID token) {
                throw new AssertionError("No explicit exhaustion is permitted");
            }
        }
        RecordingPort port = new RecordingPort();
        ExternalEffectPort failing = new ExternalEffectPort() {
            @Override public ExecuteOutcome execute(UUID eventId, dev.vra.async.contract.ReservationCreatedEventV1.Payload payload) {
                throw new AssertionError("Reconciler cannot execute");
            }
            @Override public Observation observe(UUID eventId) {
                throw new IllegalStateException("Transport uncertainty");
            }
        };
        assertEquals(1, new ReconciliationService(port, failing, policy(() -> 0))
                .reconcileAvailable("worker", 1, Duration.ofSeconds(30)));
        assertEquals(1, port.waits);
    }
}
