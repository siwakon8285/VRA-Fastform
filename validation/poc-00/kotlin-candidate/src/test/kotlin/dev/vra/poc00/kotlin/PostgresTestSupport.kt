package dev.vra.poc00.kotlin

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import org.testcontainers.containers.PostgreSQLContainer

object PostgresTestSupport {
    fun postgres(): PostgreSQLContainer<Nothing> {
        val container = PostgreSQLContainer<Nothing>("postgres:17.11")
        container.withDatabaseName("vra_poc00")
        container.withUsername("vra_poc00")
        container.withCommand("postgres")
        container.withCreateContainerCmdModifier { command ->
            command.hostConfig!!.withPortBindings(
                    PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort(5432))
            )
        }
        return container
    }
}
