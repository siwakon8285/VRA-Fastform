package dev.vra.poc00;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.PostgreSQLContainer;

final class PostgresTestSupport {
    private PostgresTestSupport() {}

    static PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17.11")
                .withDatabaseName("vra_poc00").withUsername("vra_poc00")
                .withCommand("postgres")
                .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), new ExposedPort(5432))));
    }
}
