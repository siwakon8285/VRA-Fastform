package dev.vra.async.simulator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Launches only the test simulator, without Stage-I worker-process machinery. */
public final class SimulatorProcessHarness implements AutoCloseable {
    private final Process process;
    private final URI baseUri;

    public SimulatorProcessHarness(PostgreSQLContainer database) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", testClasspath(), SimulatorMain.class.getName());
        builder.redirectErrorStream(true);
        builder.environment().put("SIM_DB_URL", database.getJdbcUrl());
        builder.environment().put("SIM_DB_USER", database.getUsername());
        builder.environment().put("SIM_DB_PASSWORD", database.getPassword());
        process = builder.start();
        var reader = Executors.newSingleThreadExecutor();
        try {
            BufferedReader output = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
            String ready = reader.submit(() -> {
                String line;
                while ((line = output.readLine()) != null) {
                    if (line.startsWith("SIMULATOR_READY ")) return line;
                }
                throw new IllegalStateException("Simulator exited before readiness");
            }).get(20, TimeUnit.SECONDS);
            String[] parts = ready.split(" ");
            if (parts.length != 3 || Long.parseLong(parts[2]) != process.pid()) {
                throw new IllegalStateException("Simulator PID handshake failed");
            }
            baseUri = URI.create("http://127.0.0.1:" + Integer.parseInt(parts[1]));
        } catch (Exception failure) {
            process.destroyForcibly();
            throw failure;
        } finally {
            reader.shutdownNow();
        }
    }

    public URI baseUri() { return baseUri; }
    public long pid() { return process.pid(); }
    public boolean alive() { return process.isAlive(); }

    private static String testClasspath() {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        for (ClassLoader loader = SimulatorMain.class.getClassLoader(); loader != null;
                loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (URL url : urls.getURLs()) {
                    if ("file".equals(url.getProtocol())) {
                        try { entries.add(Path.of(url.toURI()).toString()); }
                        catch (Exception ignored) { /* Non-file classpath entries are not launch inputs. */ }
                    }
                }
            }
        }
        if (entries.isEmpty()) return System.getProperty("java.class.path");
        return String.join(System.getProperty("path.separator"), entries);
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
