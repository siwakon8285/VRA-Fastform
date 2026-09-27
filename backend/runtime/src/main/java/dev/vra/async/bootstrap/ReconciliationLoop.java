package dev.vra.async.bootstrap;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

import dev.vra.async.application.reconciliation.ReconciliationService;

/** Bounded PostgreSQL rescan; process memory is never recovery authority. */
public final class ReconciliationLoop implements Runnable {
    private final ReconciliationService service;
    private final Duration lease;
    private final Duration pollInterval;
    private final int batchSize;
    private final String workerRef;
    private final AsyncDrainCoordinator drain;
    private final Duration drainGrace;

    public ReconciliationLoop(ReconciliationService service, Duration lease, Duration pollInterval,
            int batchSize, String workerRef) {
        this(service, lease, pollInterval, batchSize, batchSize, workerRef);
    }

    public ReconciliationLoop(ReconciliationService service, Duration lease, Duration pollInterval,
            int batchSize, int maxInFlight, String workerRef) {
        this(service, lease, pollInterval, batchSize, workerRef,
                new AsyncDrainCoordinator(maxInFlight, "reconciliation-attempt"));
    }

    public ReconciliationLoop(ReconciliationService service, Duration lease, Duration pollInterval,
            int batchSize, String workerRef, AsyncDrainCoordinator drain) {
        this.service = Objects.requireNonNull(service);
        this.lease = Objects.requireNonNull(lease);
        this.pollInterval = Objects.requireNonNull(pollInterval);
        if (batchSize < 1 || batchSize > 128 || workerRef == null || workerRef.isBlank()) {
            throw new IllegalArgumentException("Invalid reconciler loop bounds");
        }
        this.batchSize = batchSize;
        this.workerRef = workerRef;
        this.drain = Objects.requireNonNull(drain);
        this.drainGrace = lease;
    }

    public int pollOnce() {
        synchronized (drain.admission()) {
            if (!drain.accepting()) return 0;
            int reserved = 0;
            while (reserved < batchSize && drain.reserve()) reserved++;
            if (reserved == 0) return 0;
            try { return service.reconcileAvailable(workerRef, reserved, lease); }
            finally { for (int i = 0; i < reserved; i++) drain.release(); }
        }
    }

    private void dispatchBounded() {
        synchronized (drain.admission()) {
            if (!drain.accepting()) return;
            int scheduled = 0;
            while (scheduled < batchSize && drain.reserve()) {
                try {
                    drain.submit(() -> {
                        if (!drain.beginWork()) return;
                        try { service.reconcileAvailable(workerRef, 1, lease); }
                        catch (RuntimeException uncertain) {
                            System.err.println("Reconciliation claim retained for PostgreSQL rediscovery: "
                                    + uncertain.getClass().getSimpleName());
                        }
                    });
                    scheduled++;
                } catch (RuntimeException rejected) {
                    drain.release();
                    throw rejected;
                }
            }
        }
    }

    public void drain() { drain.drain(drainGrace); }

    @Override
    public void run() {
        while (drain.accepting() && !Thread.currentThread().isInterrupted()) {
            dispatchBounded();
            LockSupport.parkNanos(pollInterval.toNanos());
        }
    }
}
