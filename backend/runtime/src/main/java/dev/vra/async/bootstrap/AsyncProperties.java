package dev.vra.async.bootstrap;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** POC runtime tuning only; cycle budgets are persisted by PostgreSQL. */
@Component
@ConfigurationProperties(prefix = "vra.async")
public class AsyncProperties {
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration processingTimeout = Duration.ofSeconds(10);
    private Duration retryBaseDelay = Duration.ofMillis(200);
    private Duration retryMaxDelay = Duration.ofSeconds(5);
    private double retryJitterFraction = 0.20;
    private Duration pollInterval = Duration.ofMillis(200);
    private int batchSize = 8;
    private int maxInFlight = 8;
    private Duration reconciliationLeaseDuration = Duration.ofSeconds(30);
    private Duration reconciliationTimeout = Duration.ofSeconds(10);
    private Duration reconciliationBaseDelay = Duration.ofMillis(500);
    private Duration reconciliationMaxDelay = Duration.ofSeconds(5);

    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration value) { leaseDuration = value; }
    public Duration getProcessingTimeout() { return processingTimeout; }
    public void setProcessingTimeout(Duration value) { processingTimeout = value; }
    public Duration getRetryBaseDelay() { return retryBaseDelay; }
    public void setRetryBaseDelay(Duration value) { retryBaseDelay = value; }
    public Duration getRetryMaxDelay() { return retryMaxDelay; }
    public void setRetryMaxDelay(Duration value) { retryMaxDelay = value; }
    public double getRetryJitterFraction() { return retryJitterFraction; }
    public void setRetryJitterFraction(double value) { retryJitterFraction = value; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration value) { pollInterval = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    public int getMaxInFlight() { return maxInFlight; }
    public void setMaxInFlight(int value) { maxInFlight = value; }
    public Duration getReconciliationLeaseDuration() { return reconciliationLeaseDuration; }
    public void setReconciliationLeaseDuration(Duration value) { reconciliationLeaseDuration = value; }
    public Duration getReconciliationTimeout() { return reconciliationTimeout; }
    public void setReconciliationTimeout(Duration value) { reconciliationTimeout = value; }
    public Duration getReconciliationBaseDelay() { return reconciliationBaseDelay; }
    public void setReconciliationBaseDelay(Duration value) { reconciliationBaseDelay = value; }
    public Duration getReconciliationMaxDelay() { return reconciliationMaxDelay; }
    public void setReconciliationMaxDelay(Duration value) { reconciliationMaxDelay = value; }
}
