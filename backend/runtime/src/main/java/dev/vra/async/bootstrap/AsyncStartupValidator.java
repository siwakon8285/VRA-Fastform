package dev.vra.async.bootstrap;

import java.time.Duration;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Validates the bounded Stage-C tuning surface after property binding. */
@Component
public class AsyncStartupValidator implements InitializingBean {
    private final AsyncProperties properties;

    public AsyncStartupValidator(AsyncProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        validate(properties);
    }

    public static void validate(AsyncProperties p) {
        long lease = milliseconds(p.getLeaseDuration(), "leaseDuration");
        long processing = milliseconds(p.getProcessingTimeout(), "processingTimeout");
        long retryBase = milliseconds(p.getRetryBaseDelay(), "retryBaseDelay");
        long retryMax = milliseconds(p.getRetryMaxDelay(), "retryMaxDelay");
        milliseconds(p.getPollInterval(), "pollInterval");
        long reconciliationLease = milliseconds(p.getReconciliationLeaseDuration(), "reconciliationLeaseDuration");
        long reconciliationTimeout = milliseconds(p.getReconciliationTimeout(), "reconciliationTimeout");
        long reconciliationBase = milliseconds(p.getReconciliationBaseDelay(), "reconciliationBaseDelay");
        long reconciliationMax = milliseconds(p.getReconciliationMaxDelay(), "reconciliationMaxDelay");
        if (processing >= lease || reconciliationTimeout >= reconciliationLease
                || retryMax < retryBase || reconciliationMax < reconciliationBase
                || !Double.isFinite(p.getRetryJitterFraction())
                || p.getRetryJitterFraction() < 0 || p.getRetryJitterFraction() > 0.5
                || p.getBatchSize() < 1 || p.getBatchSize() > 128
                || p.getMaxInFlight() < 1 || p.getBatchSize() > p.getMaxInFlight()) {
            throw new IllegalArgumentException("Invalid asynchronous tuning relationship or concurrency bound");
        }
    }

    private static long milliseconds(Duration duration, String name) {
        if (duration == null) throw new IllegalArgumentException(name + " is required");
        long value;
        try {
            value = duration.toMillis();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException(name + " overflows milliseconds", error);
        }
        if (value < 1 || value > 86_400_000) {
            throw new IllegalArgumentException(name + " must be 1..86400000 ms");
        }
        return value;
    }
}
