package dev.vra.poc04.external;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/** TEST-only supported lifecycle hooks bind exact returned IDs before PostgreSQL starts. */
public final class RunOwnedPostgreSQLContainer extends PostgreSQLContainer {
    private String instanceId;
    private String volumeName;
    private String networkId;

    public RunOwnedPostgreSQLContainer(String image) { super(image); }
    public RunOwnedPostgreSQLContainer(DockerImageName image) { super(image); }

    public Map<String, Object> currentJdbcEndpoint() {
        // Raw Docker restart may remap ephemeral ports; getContainerInfo() is cached by Testcontainers.
        InspectContainerResponse current = getCurrentContainerInfo();
        var bindings = current.getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(POSTGRESQL_PORT));
        if (!Boolean.TRUE.equals(current.getState().getRunning()) || bindings == null || bindings.length == 0)
            throw new IllegalStateException("Current PostgreSQL endpoint is not running/published");
        int port = Integer.parseInt(bindings[0].getHostPortSpec());
        if (port <= 0 || port > 65535) throw new IllegalStateException("Invalid current mapped PostgreSQL port");
        String host = getHost();
        String urlHost = host.contains(":") ? "[" + host + "]" : host;
        String url = "jdbc:postgresql://" + urlHost + ":" + port + "/" + getDatabaseName()
                + constructUrlParameters("?", "&");
        return Map.of("container_id", current.getId(), "host", host, "mapped_port", port, "jdbc_url", url);
    }

    @Override
    public String getJdbcUrl() { return (String) currentJdbcEndpoint().get("jdbc_url"); }

    @Override
    protected void containerIsCreated(String id) {
        InspectContainerResponse actual = getDockerClient().inspectContainerCmd(id).exec();
        instanceId = actual.getConfig().getLabels().get("dev.vra.database_instance_id");
        if (instanceId == null) throw new IllegalStateException("Creator allocation missing before PostgreSQL start");
        JsonNode allocation = RunResources.harness("allocation", instanceId);
        Map<String, String> expected = new java.util.LinkedHashMap<>();
        allocation.path("labels").properties().forEach(entry -> expected.put(entry.getKey(), entry.getValue().asString()));
        RunResources.check(expected, actual.getConfig().getLabels());
        RunResources.harness("register", "--kind", "container", "--id", id, "--instance", instanceId);
        if (actual.getMounts().size() != 1) throw new IllegalStateException("PostgreSQL must have one fresh PGDATA mount");
        var mount = actual.getMounts().getFirst();
        if (!"/var/lib/postgresql/data".equals(mount.getDestination().getPath()) || !Boolean.TRUE.equals(mount.getRW()))
            throw new IllegalStateException("PostgreSQL PGDATA destination or write mode mismatch");
        String stem = "vra-poc04-" + expected.get("dev.vra.run_id") + "-" + allocation.path("logical_name").asString().replace('/', '-');
        if (!actual.getName().equals("/" + stem) || !mount.getName().equals(stem + "-pgdata"))
            throw new IllegalStateException("PostgreSQL allocated resource name mismatch");
        String observedVolume = mount.getName();
        String observedNetwork = actual.getHostConfig().getNetworkMode();
        // Observation can only verify already creator-registered resources, never add deletion authority.
        JsonNode authorizedVolume = RunResources.harness("authorize", "--kind", "volume", "--id", observedVolume);
        JsonNode authorizedNetwork = RunResources.harness("authorize", "--kind", "network", "--id", observedNetwork);
        if (!instanceId.equals(authorizedVolume.path("database_instance_id").asString())
                || !instanceId.equals(authorizedNetwork.path("database_instance_id").asString()))
            throw new IllegalStateException("PostgreSQL mount/network belongs to another creator allocation");
        volumeName = observedVolume;
        networkId = observedNetwork;
        RunResources.record(Map.of("event", "container_created_attested_before_start", "id", id,
                "name", actual.getName(), "created", actual.getCreated(), "labels", actual.getConfig().getLabels(),
                "database_instance_id", instanceId, "logical_name", allocation.path("logical_name").asString(),
                "database_name", getDatabaseName(), "database_user", getUsername(), "network_id", networkId));
        super.containerIsCreated(id);
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse actual) {
        super.containerIsStarted(actual);
        JsonNode physical = RunResources.harness("bind-physical", "--instance", instanceId, "--container", actual.getId(),
                "--database", getDatabaseName(), "--user", getUsername());
        RunResources.record(Map.of("event", "postgres_physical_identity_after_readiness", "identity", physical));
    }

    @Override
    public void stop() {
        String id = getContainerId();
        if (id == null) return;
        // Native Testcontainers lifecycle remains unchanged after current-ledger/live-ownership authorization.
        RunResources.harness("authorize", "--kind", "container", "--id", id);
        super.stop();
        RunResources.harness("teardown", "--kind", "container", "--id", id);
        if (networkId != null) RunResources.harness("teardown", "--kind", "network", "--id", networkId);
        if (volumeName != null) RunResources.harness("teardown", "--kind", "volume", "--id", volumeName);
    }
}
