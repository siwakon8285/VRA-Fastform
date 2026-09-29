package dev.vra.async.application.reconciliation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import dev.vra.async.application.delivery.JitterSource;
import dev.vra.async.application.external.ExternalEffectPort.Observation;
import dev.vra.async.application.reconciliation.ReconciliationPort.Claim;
import dev.vra.async.application.reconciliation.ReconciliationPort.DeliveryCycle;

/** Pure timing and observation policy; persisted counts and PostgreSQL decide eligibility. */
public final class ReconciliationPolicy {
    private static final long DB_MAX_DELAY_MS = 86_400_000;
    public enum Decision { CONFIRM_SUCCESS, CONFIRM_NO_EFFECT, WAIT_INDETERMINATE }

    private final long queryBaseMs;
    private final long queryMaxMs;
    private final long retryBaseMs;
    private final long retryMaxMs;
    private final double jitterFraction;
    private final JitterSource jitter;

    public ReconciliationPolicy(Duration reconciliationBaseDelay, Duration reconciliationMaxDelay,
            Duration retryBaseDelay, Duration retryMaxDelay, double jitterFraction, JitterSource jitter) {
        this.queryBaseMs = milliseconds(reconciliationBaseDelay);
        this.queryMaxMs = milliseconds(reconciliationMaxDelay);
        this.retryBaseMs = milliseconds(retryBaseDelay);
        this.retryMaxMs = milliseconds(retryMaxDelay);
        if (queryMaxMs < queryBaseMs || retryMaxMs < retryBaseMs
                || !Double.isFinite(jitterFraction) || jitterFraction < 0 || jitterFraction > 0.5) {
            throw new IllegalArgumentException("Invalid delay or jitter range");
        }
        this.jitterFraction = jitterFraction;
        this.jitter = Objects.requireNonNull(jitter, "jitter");
    }

    public Decision decide(Observation observation) {
        return switch (Objects.requireNonNull(observation, "observation")) {
            case CONFIRMED_SUCCEEDED -> Decision.CONFIRM_SUCCESS;
            case CONFIRMED_NO_EFFECT -> Decision.CONFIRM_NO_EFFECT;
            case INDETERMINATE -> Decision.WAIT_INDETERMINATE;
        };
    }

    public Optional<Duration> nextQueryDelay(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        return claim.cycleClaimCount() < claim.cycleClaimLimit()
                ? Optional.of(delay(queryBaseMs, queryMaxMs, claim.cycleClaimCount())) : Optional.empty();
    }

    public Optional<Duration> noEffectRetryDelay(DeliveryCycle cycle) {
        Objects.requireNonNull(cycle, "cycle");
        return cycle.cycleClaimCount() < cycle.cycleClaimLimit()
                ? Optional.of(delay(retryBaseMs, retryMaxMs, cycle.cycleClaimCount())) : Optional.empty();
    }

    private Duration delay(long base, long maximum, int claimCount) {
        long capped = base;
        for (int step = 1; step < claimCount && capped < maximum; step++) {
            capped = capped > maximum / 2 ? maximum : Math.min(maximum, capped * 2);
        }
        double sample = jitter.sampleUnit();
        if (!Double.isFinite(sample) || sample < -1 || sample > 1) {
            throw new IllegalArgumentException("Jitter sample must be within [-1,1]");
        }
        long result = Math.round(capped * (1 + sample * jitterFraction));
        return Duration.ofMillis(Math.max(1, Math.min(DB_MAX_DELAY_MS, result)));
    }

    private static long milliseconds(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long value;
        try {
            value = duration.toMillis();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Duration overflows milliseconds", error);
        }
        if (value < 1 || value > DB_MAX_DELAY_MS) {
            throw new IllegalArgumentException("Duration must be 1..86400000 ms");
        }
        return value;
    }
}
