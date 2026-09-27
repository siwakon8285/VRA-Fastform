package dev.vra.async.application.delivery;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import dev.vra.async.application.delivery.DeliveryFailureClassifier.Classification;
import dev.vra.async.application.delivery.DeliveryFailureClassifier.FailureClass;
import dev.vra.async.application.delivery.DeliveryPort.ClaimedDelivery;
import dev.vra.async.application.delivery.DeliveryPort.TargetCode;

/** Computes delay only; PostgreSQL owns eligibility and the persisted cycle limit. */
public final class DeliveryRetryPolicy {
    private static final long DB_MAX_DELAY_MS = 86_400_000;
    private final long baseMs;
    private final long maxMs;
    private final double jitterFraction;
    private final JitterSource jitter;

    public DeliveryRetryPolicy(Duration retryBaseDelay, Duration retryMaxDelay,
            double retryJitterFraction, JitterSource jitter) {
        this.baseMs = validatedMillis(retryBaseDelay, "retryBaseDelay");
        this.maxMs = validatedMillis(retryMaxDelay, "retryMaxDelay");
        if (maxMs < baseMs) {
            throw new IllegalArgumentException("retryMaxDelay must be at least retryBaseDelay");
        }
        if (!Double.isFinite(retryJitterFraction)
                || retryJitterFraction < 0.0 || retryJitterFraction > 0.5) {
            throw new IllegalArgumentException("retryJitterFraction must be finite and within [0,0.5]");
        }
        this.jitterFraction = retryJitterFraction;
        this.jitter = Objects.requireNonNull(jitter, "jitter");
    }

    private static long validatedMillis(Duration duration, String name) {
        if (duration == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + " overflows milliseconds", overflow);
        }
        if (millis < 1 || millis > DB_MAX_DELAY_MS) {
            throw new IllegalArgumentException(name + " must be within [1,86400000] milliseconds");
        }
        return millis;
    }

    public Optional<Duration> nextDelay(ClaimedDelivery claim, Classification failure) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(failure, "failure");
        if (claim.requiresReconciliationBeforeExecute()) {
            throw new IllegalStateException("Reclaimed external attempt requires UNKNOWN handoff");
        }
        if (claim.targetCode() == TargetCode.VALIDATION_EXTERNAL_EFFECT) {
            throw new IllegalStateException("External retry requires a later explicit before-send policy");
        }
        if (failure.failureClass() != FailureClass.RETRYABLE_TRANSIENT
                || !"RETRYABLE_TRANSIENT".equals(failure.reasonCode())) {
            throw new IllegalArgumentException("Only proven retryable transient evidence may be retried");
        }
        if (claim.cycleClaimCount() < 1 || claim.cycleClaimLimit() < 1
                || claim.cycleClaimCount() > claim.cycleClaimLimit()) {
            throw new IllegalArgumentException("Invalid persisted delivery cycle evidence");
        }
        if (claim.cycleClaimCount() == claim.cycleClaimLimit()) {
            return Optional.empty();
        }

        long capped = baseMs;
        for (int n = 1; n < claim.cycleClaimCount() && capped < maxMs; n++) {
            capped = capped > maxMs / 2 ? maxMs : Math.min(maxMs, capped * 2);
        }
        double sample = jitter.sampleUnit();
        if (!Double.isFinite(sample) || sample < -1.0 || sample > 1.0) {
            throw new IllegalArgumentException("Jitter sample must be finite and within [-1,1]");
        }
        long millis = Math.round(Math.min(DB_MAX_DELAY_MS, capped * (1.0 + sample * jitterFraction)));
        return Optional.of(Duration.ofMillis(Math.max(1, millis)));
    }
}
