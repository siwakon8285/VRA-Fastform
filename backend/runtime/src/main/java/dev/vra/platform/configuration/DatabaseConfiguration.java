package dev.vra.platform.configuration;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RuntimeDatabaseProperties.class)
public class DatabaseConfiguration {

    @Bean
    DataSource dataSource(RuntimeDatabaseProperties properties) {
        return DataSourceBuilder.create()
                .url(properties.url())
                .username(properties.username())
                .password(properties.password())
                .build();
    }
}
