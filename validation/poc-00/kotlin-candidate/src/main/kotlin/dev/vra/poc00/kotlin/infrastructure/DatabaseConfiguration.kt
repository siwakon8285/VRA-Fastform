package dev.vra.poc00.kotlin.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
class DatabaseConfiguration {
    @Bean
    fun dataSource(environment: Environment): HikariDataSource {
        val settings = LocalDatabaseSettings(
            environment.getProperty("VRA_DB_URL") ?: LocalDatabaseSettings.DEFAULT_URL,
            environment.getProperty("VRA_DB_USERNAME") ?: "vra_poc00",
            environment.getProperty("VRA_DB_PASSWORD")
        )
        val config = HikariConfig().apply {
            jdbcUrl = settings.url
            username = settings.username
            password = settings.password
            maximumPoolSize = 4
        }
        return HikariDataSource(config)
    }
}
