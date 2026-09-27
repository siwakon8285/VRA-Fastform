package dev.vra.async;

import java.time.Duration;

import dev.vra.async.bootstrap.AsyncProperties;
import dev.vra.async.bootstrap.AsyncStartupValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsyncPropertiesTest {
    @Test
    void approvedDefaultsAreValidAndContainNoCycleBudgetProperty() {
        AsyncProperties p = new AsyncProperties();
        AsyncStartupValidator.validate(p);
        assertEquals(Duration.ofSeconds(30), p.getLeaseDuration());
        assertEquals(Duration.ofSeconds(10), p.getProcessingTimeout());
        assertEquals(Duration.ofMillis(200), p.getRetryBaseDelay());
        assertEquals(Duration.ofSeconds(5), p.getRetryMaxDelay());
        assertEquals(0.20, p.getRetryJitterFraction());
        assertEquals(Duration.ofMillis(200), p.getPollInterval());
        assertEquals(8, p.getBatchSize());
        assertEquals(8, p.getMaxInFlight());
        assertEquals(Duration.ofSeconds(30), p.getReconciliationLeaseDuration());
        assertEquals(Duration.ofSeconds(10), p.getReconciliationTimeout());
        assertEquals(Duration.ofMillis(500), p.getReconciliationBaseDelay());
        assertEquals(Duration.ofSeconds(5), p.getReconciliationMaxDelay());
        assertEquals(12, AsyncProperties.class.getDeclaredFields().length);
    }

    @Test
    void rejectsInvalidTimingAndOverflow() {
        AsyncProperties p = new AsyncProperties();
        p.setLeaseDuration(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setLeaseDuration(Duration.ofSeconds(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setLeaseDuration(Duration.ofSeconds(30));
        p.setProcessingTimeout(Duration.ofSeconds(30));
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setProcessingTimeout(Duration.ofSeconds(10));
        p.setRetryMaxDelay(Duration.ofMillis(100));
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setRetryMaxDelay(Duration.ofSeconds(5));
        p.setReconciliationTimeout(Duration.ofSeconds(30));
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setReconciliationTimeout(Duration.ofSeconds(10));
        p.setReconciliationMaxDelay(Duration.ofMillis(100));
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setReconciliationMaxDelay(Duration.ofSeconds(5));
        p.setPollInterval(Duration.ofNanos(1));
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
    }

    @Test
    void rejectsInvalidJitterAndConcurrency() {
        AsyncProperties p = new AsyncProperties();
        p.setRetryJitterFraction(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setRetryJitterFraction(-0.1);
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setRetryJitterFraction(0.51);
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setRetryJitterFraction(0.5);
        p.setBatchSize(0);
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setBatchSize(9);
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setBatchSize(8);
        p.setMaxInFlight(0);
        assertThrows(IllegalArgumentException.class, () -> AsyncStartupValidator.validate(p));
        p.setMaxInFlight(8);
        AsyncStartupValidator.validate(p);
    }
}
