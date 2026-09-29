package dev.vra.async.process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs the real production entry point in a separate, inspectable JVM. */
public final class WorkerProcessHarness implements AutoCloseable {
    private final Process process;
    private final Instant startedAt = Instant.now();
    private final StringBuilder diagnostics = new StringBuilder();
    private final Thread outputReader;

    public WorkerProcessHarness(Map<String, String> configuration) throws Exception {
        String classpath = System.getProperty("vra.integrationTest.runtimeClasspath");
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("Resolved integration-test runtime classpath is required");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classpath, "dev.vra.VraApplication");
        builder.redirectErrorStream(true);
        builder.environment().putAll(configuration);
        process = builder.start();
        outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (diagnostics) {
                        diagnostics.append(line).append('\n');
                        if (diagnostics.length() > 16_384) {
                            diagnostics.delete(0, diagnostics.length() - 16_384);
                        }
                    }
                }
            } catch (Exception ignored) { /* Process exit closes the pipe. */ }
        }, "worker-process-output-" + process.pid());
        outputReader.setDaemon(true);
        outputReader.start();
    }

    public long pid() { return process.pid(); }
    public Instant startedAt() { return startedAt; }
    public boolean alive() { return process.isAlive(); }
    public String diagnostics() {
        synchronized (diagnostics) { return diagnostics.toString(); }
    }

    public int killForcibly() throws InterruptedException {
        process.destroyForcibly();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Child PID " + pid() + " survived destroyForcibly: " + diagnostics());
        }
        return process.exitValue();
    }

    public int terminateGracefully() throws InterruptedException {
        requestGracefulShutdown();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Child PID " + pid() + " did not drain in time: " + diagnostics());
        }
        return process.exitValue();
    }

    public void requestGracefulShutdown() { process.destroy(); }

    /** JVM thread state is an external process barrier, not a production test hook. */
    public boolean drainAdmissionClosed(String hookName) throws Exception {
        if (!process.isAlive()) return false;
        Path output = Files.createTempFile("vra-jcmd-", ".txt");
        try {
            Process command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "jcmd").toString(),
                    Long.toString(pid()), "Thread.print")
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!command.waitFor(2, TimeUnit.SECONDS)) {
                command.destroyForcibly();
                command.waitFor(2, TimeUnit.SECONDS);
                return false;
            }
            if (command.exitValue() != 0) return false;
            String dump = Files.readString(output);
            int hook = dump.indexOf('"' + hookName + '"');
            if (hook < 0) return false;
            int next = dump.indexOf("\n\"", hook + 1);
            String stack = dump.substring(hook, next < 0 ? dump.length() : next);
            return stack.contains("AsyncDrainCoordinator.drain")
                    && stack.contains("ThreadPoolExecutor.awaitTermination");
        } finally {
            Files.deleteIfExists(output);
        }
    }

    public int awaitExit(Duration timeout) throws InterruptedException {
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Child PID " + pid() + " did not exit: " + diagnostics());
        }
        return process.exitValue();
    }

    @Override public void close() {
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Leaked child PID " + pid());
                    }
                }
            } catch (InterruptedException interrupted) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
}
