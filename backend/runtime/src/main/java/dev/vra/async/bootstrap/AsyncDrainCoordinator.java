package dev.vra.async.bootstrap;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Process-local capacity and shutdown gate. PostgreSQL remains the work queue. */
public final class AsyncDrainCoordinator {
    private final Object admission = new Object();
    private final Semaphore capacity;
    private final ThreadPoolExecutor executor;
    private volatile boolean accepting = true;

    public AsyncDrainCoordinator(int maxInFlight, String threadPrefix) {
        if (maxInFlight < 1 || maxInFlight > 128 || threadPrefix == null || threadPrefix.isBlank()) {
            throw new IllegalArgumentException("Invalid process capacity");
        }
        capacity = new Semaphore(maxInFlight);
        executor = new ThreadPoolExecutor(maxInFlight, maxInFlight, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxInFlight), task -> {
                    Thread thread = new Thread(task, threadPrefix);
                    thread.setDaemon(false);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public Object admission() { return admission; }
    public boolean accepting() { return accepting; }
    public int available() { return capacity.availablePermits(); }
    public boolean reserve() { return accepting && capacity.tryAcquire(); }
    public void release() { capacity.release(); }

    /** Linearizes an effect/query start against the first drain instruction. */
    public boolean beginWork() {
        synchronized (admission) { return accepting; }
    }

    /** The actual database claim, including commit, shares drain's admission lock. */
    public <T> T claimIfAccepting(Supplier<T> claim, Supplier<T> rejected) {
        synchronized (admission) {
            return accepting ? claim.get() : rejected.get();
        }
    }

    public void submit(Runnable work) {
        Objects.requireNonNull(work);
        executor.execute(() -> {
            try { work.run(); }
            finally { capacity.release(); }
        });
    }

    /** Safe to call from a shutdown hook and from normal loop termination. */
    public void drain(Duration grace) {
        synchronized (admission) {
            accepting = false;
            executor.shutdown();
        }
        try {
            long wait = Math.max(1, Objects.requireNonNull(grace).toMillis());
            if (!executor.awaitTermination(wait, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(wait, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
