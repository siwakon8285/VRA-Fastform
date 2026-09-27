package dev.vra.async.bootstrap;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

import dev.vra.async.application.reconciliation.ReconciliationService;

/** Bounded PostgreSQL rescan; no process-local cursor or Stage-I drain protocol. */
public final class ReconciliationLoop implements Runnable {
    private final ReconciliationService service;
    private final Duration lease;
    private final Duration pollInterval;
    private final int batchSize;
    private final String workerRef;

    public ReconciliationLoop(ReconciliationService service, Duration lease, Duration pollInterval,
            int batchSize, String workerRef) {
        this.service = Objects.requireNonNull(service);
        this.lease = Objects.requireNonNull(lease);
        this.pollInterval = Objects.requireNonNull(pollInterval);
        if (batchSize < 1 || batchSize > 128 || workerRef == null || workerRef.isBlank()) {
            throw new IllegalArgumentException("Invalid reconciler loop bounds");
        }
        this.batchSize = batchSize;
        this.workerRef = workerRef;
    }

    public int pollOnce() {
        return service.reconcileAvailable(workerRef, batchSize, lease);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            pollOnce();
            LockSupport.parkNanos(pollInterval.toNanos());
        }
    }
}
