package dev.vra.poc00.infrastructure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseConfiguration {
    @Bean
    HikariDataSource dataSource(Environment environment) {
        var settings = new LocalDatabaseSettings(
                environment.getProperty("VRA_DB_URL", LocalDatabaseSettings.DEFAULT_URL),
                environment.getProperty("VRA_DB_USERNAME", "vra_poc00"),
                environment.getProperty("VRA_DB_PASSWORD"));
        var config = new HikariConfig();
        config.setJdbcUrl(settings.url());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(4);
        return new HikariDataSource(config);
    }
}
