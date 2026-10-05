package dev.vra.poc04.external;

import org.testcontainers.core.CreateContainerCmdModifier;
import org.testcontainers.DockerClientFactory;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Administrative TEST-only creator. Shared run state, never observed labels, allocates deletion authority. */
public final class RunResources implements CreateContainerCmdModifier {
    private static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Required TEST metadata missing: " + key);
        return value;
    }

    static JsonNode harness(String... arguments) {
        List<String> command = new ArrayList<>(List.of("python3", "-B", env("VRA_POC04_RUN_HARNESS")));
        command.addAll(List.of(arguments));
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process running = process;
            CompletableFuture<byte[]> output = new CompletableFuture<>();
            Thread.startVirtualThread(() -> {
                try { output.complete(running.getInputStream().readNBytes(65_537)); }
                catch (Exception failure) { output.completeExceptionally(failure); }
            });
            byte[] bytes = output.get(60, TimeUnit.SECONDS);
            if (bytes.length > 65_536 || !process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0)
                throw new IllegalStateException("Administrative TEST harness rejected " + arguments[0]);
            return JsonMapper.builder().build().readTree(bytes);
        } catch (Exception failure) {
            throw new IllegalStateException("Administrative TEST harness operation failed: " + arguments[0], failure);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static Map<String, String> allocatedLabels(JsonNode allocation) {
        Map<String, String> result = new LinkedHashMap<>();
        allocation.path("labels").properties().forEach(entry -> result.put(entry.getKey(), entry.getValue().asString()));
        check(Map.of("dev.vra.run_id", env("VRA_POC04_RUN_ID"),
                "dev.vra.environment", "TEST", "dev.vra.poc", "04",
                "dev.vra.source_head", env("VRA_POC04_SOURCE_HEAD"),
                "dev.vra.source_content_sha256", env("VRA_POC04_SOURCE_CONTENT_SHA256"),
                "dev.vra.tool_manifest_sha256", env("VRA_POC04_TOOL_MANIFEST_SHA256")), result);
        return result;
    }

    @Override
    public CreateContainerCmd modify(CreateContainerCmd command) {
        if (!env("DOCKER_HOST").equals(env("VRA_POC04_DISPOSABLE_TEST_DOCKER_ENDPOINT")))
            throw new IllegalStateException("Dedicated TEST endpoint mismatch");
        DockerClient client = DockerClientFactory.instance().client();
        var daemon = client.infoCmd().exec();
        if (!env("VRA_POC04_EXPECTED_DAEMON_ID").equals(daemon.getId()))
            throw new IllegalStateException("Dedicated TEST daemon physical identity mismatch");
        String platform = switch (daemon.getArchitecture()) {
            case "arm64", "aarch64" -> "linux/arm64/v8";
            case "amd64", "x86_64" -> "linux/amd64";
            default -> throw new IllegalStateException("Unreviewed execution platform");
        };
        Map<String, String> original = command.getLabels() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(command.getLabels());
        boolean alreadyBound = original.containsKey("dev.vra.database_instance_id");
        boolean postgres = command.getImage().contains("postgres");
        String database = Arrays.stream(command.getEnv() == null ? new String[0] : command.getEnv())
                .filter(value -> value.startsWith("POSTGRES_DB=")).map(value -> value.substring("POSTGRES_DB=".length()))
                .findFirst().orElse("");
        String role = postgres ? (database.startsWith("simulator_") ? "postgres-simulator" : "postgres-authority") : "testcontainers-helper";
        JsonNode allocation = alreadyBound
                ? harness("allocation", original.get("dev.vra.database_instance_id")) : harness("allocate", role);
        Map<String, String> bound = allocatedLabels(allocation);
        String instance = allocation.path("database_instance_id").asString();
        String stem = "vra-poc04-" + bound.get("dev.vra.run_id") + "-" + allocation.path("logical_name").asString().replace('/', '-');
        String networkId = null;
        String volumeName = null;
        if (alreadyBound) {
            check(bound, original);
            if (command.getName() != null && !stem.equals(command.getName()))
                throw new IllegalStateException("Preallocated container name mismatch");
            command.withName(stem);
        } else {
            if (original.keySet().stream().anyMatch(key -> key.startsWith("dev.vra.")))
                throw new IllegalStateException("Partial unauthorized TEST binding");
            original.putAll(bound);
            command.withLabels(original).withName(stem).withPlatform(platform);
            if (postgres) {
                if (command.getHostConfig().getBinds() != null && command.getHostConfig().getBinds().length > 0)
                    throw new IllegalStateException("Unexpected historical PostgreSQL bind mount");
                if (command.getHostConfig().getMounts() != null && !command.getHostConfig().getMounts().isEmpty())
                    throw new IllegalStateException("Unexpected historical PostgreSQL mount");
                String networkName = stem + "-network";
                volumeName = stem + "-pgdata";
                for (Network network : client.listNetworksCmd().withNameFilter(networkName).exec())
                    if (networkName.equals(network.getName())) throw new IllegalStateException("Fresh run network already exists");
                for (InspectVolumeResponse volume : client.listVolumesCmd().exec().getVolumes())
                    if (volumeName.equals(volume.getName())) throw new IllegalStateException("Fresh run volume already exists");
                networkId = client.createNetworkCmd().withName(networkName).withDriver("bridge").withLabels(bound).exec().getId();
                harness("register", "--kind", "network", "--id", networkId, "--instance", instance);
                var volume = client.createVolumeCmd().withName(volumeName).withLabels(bound).exec();
                harness("register", "--kind", "volume", "--id", volume.getName(), "--instance", instance);
                command.getHostConfig().withNetworkMode(networkId).withMounts(List.of(new Mount().withType(MountType.VOLUME)
                        .withSource(volumeName).withTarget("/var/lib/postgresql/data").withReadOnly(false)));
            }
        }
        // Testcontainers 2.0.5 applies modifier returns only locally. Creation callbacks register container IDs.
        return command;
    }

    static void check(Map<String, String> required, Map<String, String> actual) {
        for (var entry : required.entrySet())
            if (actual == null || !entry.getValue().equals(actual.get(entry.getKey())))
                throw new IllegalStateException("Physical TEST resource label mismatch: " + entry.getKey());
    }

    static synchronized void record(Map<String, Object> value) {
        try {
            byte[] bytes = (JsonMapper.builder().build().writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel file = FileChannel.open(Path.of(env("VRA_POC04_RESOURCE_EVIDENCE")), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND); FileLock lock = file.lock()) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) file.write(buffer);
                file.force(true);
            }
        } catch (Exception failure) { throw new IllegalStateException("Safe creator evidence write failed", failure); }
    }
}
