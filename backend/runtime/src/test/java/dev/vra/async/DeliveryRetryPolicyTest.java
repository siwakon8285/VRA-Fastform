package dev.vra.async;

import java.time.Duration;
import java.util.UUID;

import dev.vra.async.application.delivery.DeliveryFailureClassifier;
import dev.vra.async.application.delivery.DeliveryFailureClassifier.Evidence;
import dev.vra.async.application.delivery.DeliveryFailureClassifier.FailureClass;
import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;
import dev.vra.async.application.delivery.DeliveryRetryPolicy;
import dev.vra.async.application.delivery.JitterSource;
import dev.vra.async.bootstrap.AsyncProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryRetryPolicyTest {
    private final DeliveryFailureClassifier classifier = new DeliveryFailureClassifier();

    @Test
    void deterministicJitterAndExponentialGrowthFollowApprovedFormula() {
        var transientFailure = classifier.classify(Evidence.ROLLED_BACK_CONSUMER_TRANSIENT);
        AsyncProperties properties = new AsyncProperties();
        assertEquals(Duration.ofMillis(200), policy(properties, () -> 0)
                .nextDelay(claim(1, 5), transientFailure).orElseThrow());
        assertEquals(Duration.ofMillis(160), policy(properties, () -> -1)
                .nextDelay(claim(1, 5), transientFailure).orElseThrow());
        assertEquals(Duration.ofMillis(240), policy(properties, () -> 1)
                .nextDelay(claim(1, 5), transientFailure).orElseThrow());
        assertEquals(Duration.ofMillis(400), policy(properties, () -> 0)
                .nextDelay(claim(2, 5), transientFailure).orElseThrow());
        assertEquals(Duration.ofMillis(800), policy(properties, () -> 0)
                .nextDelay(claim(3, 5), transientFailure).orElseThrow());
        assertEquals(Duration.ofMillis(5_000), policy(properties, () -> 0)
                .nextDelay(claim(6, 16), transientFailure).orElseThrow());
        assertEquals(Duration.ofMillis(6_000), policy(properties, () -> 1)
                .nextDelay(claim(6, 16), transientFailure).orElseThrow());
    }

    @Test
    void persistedBudgetAndBoundsCannotBeExpandedByLocalConfiguration() {
        var transientFailure = classifier.classify(Evidence.ROLLED_BACK_CONSUMER_TRANSIENT);
        AsyncProperties properties = new AsyncProperties();
        DeliveryRetryPolicy policy = policy(properties, () -> 0);
        assertTrue(policy.nextDelay(claim(5, 5), transientFailure).isEmpty());
        assertTrue(policy.nextDelay(claim(3, 3), transientFailure).isEmpty());
        properties.setRetryBaseDelay(Duration.ofSeconds(2));
        properties.setRetryMaxDelay(Duration.ofSeconds(20));
        DeliveryRetryPolicy changed = policy(properties, () -> 0);
        assertTrue(changed.nextDelay(claim(5, 5), transientFailure).isEmpty());
        assertTrue(changed.nextDelay(claim(3, 3), transientFailure).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> claim(6, 5));
    }

    @Test
    void largeCountAndMillisecondBoundDoNotOverflow() {
        var transientFailure = classifier.classify(Evidence.ROLLED_BACK_CONSUMER_TRANSIENT);
        AsyncProperties properties = new AsyncProperties();
        assertEquals(Duration.ofMillis(5_000), policy(properties, () -> 0)
                .nextDelay(claim(1_000_000, 1_000_001), transientFailure).orElseThrow());
        properties.setRetryBaseDelay(Duration.ofMillis(1));
        properties.setRetryMaxDelay(Duration.ofMillis(1));
        properties.setRetryJitterFraction(0.5);
        assertEquals(Duration.ofMillis(1), policy(properties, () -> -1)
                .nextDelay(claim(1, 5), transientFailure).orElseThrow());
        properties.setRetryBaseDelay(Duration.ofMillis(86_400_000));
        properties.setRetryMaxDelay(Duration.ofMillis(86_400_000));
        assertEquals(Duration.ofMillis(86_400_000), policy(properties, () -> 1)
                .nextDelay(claim(1, 5), transientFailure).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> policy(new AsyncProperties(), () -> 2)
                .nextDelay(claim(1, 5), transientFailure));
    }

    @Test
    void constructorValidatesOnlyRetryInputs() {
        Duration base = Duration.ofMillis(200);
        Duration max = Duration.ofSeconds(5);
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(null, max, 0.2, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(base, null, 0.2, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(Duration.ZERO, max, 0.2, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(Duration.ofMillis(-1), max, 0.2, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(Duration.ofNanos(1), max, 0.2, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(max, base, 0.2, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(base, max, Double.NaN, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(base, max, Double.POSITIVE_INFINITY, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(base, max, -0.01, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(base, max, 0.51, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(Duration.ofSeconds(Long.MAX_VALUE), max, 0.2, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(base, Duration.ofSeconds(Long.MAX_VALUE), 0.2, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryRetryPolicy(base, Duration.ofDays(2), 0.2, () -> 0));
        assertThrows(NullPointerException.class, () -> new DeliveryRetryPolicy(base, max, 0.2, null));
        assertDoesNotThrow(() -> new DeliveryRetryPolicy(base, Duration.ofDays(1), 0.5, () -> 0));
    }

    @Test
    void classifierRequiresExplicitEvidenceAndNeverRetriesGenericOrUnknownFailure() {
        assertEquals(new DeliveryFailureClassifier.Classification(FailureClass.RETRYABLE_TRANSIENT,
                "RETRYABLE_TRANSIENT"), classifier.classify(Evidence.ROLLED_BACK_CONSUMER_TRANSIENT));
        assertEquals(new DeliveryFailureClassifier.Classification(FailureClass.POISON,
                "UNSUPPORTED_EVENT_CONTRACT"), classifier.classify(Evidence.UNSUPPORTED_EVENT_CONTRACT));
        assertEquals(new DeliveryFailureClassifier.Classification(FailureClass.NON_RETRYABLE,
                "NON_RETRYABLE"), classifier.classify(Evidence.NON_RETRYABLE_VALIDATION));
        assertEquals(new DeliveryFailureClassifier.Classification(FailureClass.OPERATOR_REQUIRED,
                "OPERATOR_REQUIRED"), classifier.classify(Evidence.OPERATOR_REVIEW_REQUIRED));
        assertEquals(FailureClass.OPERATOR_REQUIRED,
                classifier.classify(Evidence.UNPROVEN_CAUGHT_FAILURE).failureClass());
        assertEquals(FailureClass.UNKNOWN_OUTCOME,
                classifier.classify(Evidence.EXTERNAL_OUTCOME_UNCERTAIN).failureClass());
        DeliveryRetryPolicy policy = policy(new AsyncProperties(), () -> 0);
        assertThrows(IllegalArgumentException.class, () -> policy.nextDelay(claim(1, 5),
                classifier.classify(Evidence.UNPROVEN_CAUGHT_FAILURE)));
        assertThrows(IllegalArgumentException.class, () -> policy.nextDelay(claim(1, 5),
                classifier.classify(Evidence.EXTERNAL_OUTCOME_UNCERTAIN)));
        assertThrows(IllegalStateException.class, () -> policy.nextDelay(
                new ClaimedDelivery(UUID.randomUUID(), UUID.randomUUID(),
                        TargetCode.VALIDATION_EXTERNAL_EFFECT, true, 2, 1, 2, 5),
                classifier.classify(Evidence.ROLLED_BACK_CONSUMER_TRANSIENT)));
        assertThrows(IllegalStateException.class, () -> policy.nextDelay(
                new ClaimedDelivery(UUID.randomUUID(), UUID.randomUUID(),
                        TargetCode.VALIDATION_EXTERNAL_EFFECT, false, 1, 1, 1, 5),
                classifier.classify(Evidence.ROLLED_BACK_CONSUMER_TRANSIENT)));
    }

    private static ClaimedDelivery claim(int count, int limit) {
        return new ClaimedDelivery(UUID.randomUUID(), UUID.randomUUID(),
                TargetCode.RESERVATION_PROJECTION, false, count, 1, count, limit);
    }

    private static DeliveryRetryPolicy policy(AsyncProperties properties, JitterSource jitter) {
        return new DeliveryRetryPolicy(properties.getRetryBaseDelay(), properties.getRetryMaxDelay(),
                properties.getRetryJitterFraction(), jitter);
    }
}
