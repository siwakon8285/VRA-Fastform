package dev.vra.platform.configuration;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vra.database")
public record RuntimeDatabaseProperties(
        String url,
        String username,
        String password
) {

    private static final String POSTGRES_JDBC_PREFIX =
            "jdbc:postgresql://";

    public RuntimeDatabaseProperties {
        url = requirePostgresJdbcUrl(url);
        username = requireNonBlank(username, "username");
        password = requireNonBlank(password, "password");
    }

    private static String requirePostgresJdbcUrl(String value) {
        String url = requireNonBlank(value, "url");

        if (!url.startsWith(POSTGRES_JDBC_PREFIX)) {
            throw new IllegalArgumentException(
                    "vra.database.url must use jdbc:postgresql://"
            );
        }

        URI parsed;
        try {
            parsed = URI.create(url.substring("jdbc:".length()));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "vra.database.url must be a valid PostgreSQL JDBC URL",
                    error
            );
        }

        if (!"postgresql".equals(parsed.getScheme())
                || parsed.getHost() == null
                || parsed.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "vra.database.url must include a PostgreSQL host"
            );
        }

        String databasePath = parsed.getPath();
        if (databasePath == null
                || databasePath.length() <= 1
                || databasePath.substring(1).isBlank()) {
            throw new IllegalArgumentException(
                    "vra.database.url must include a database name"
            );
        }

        if (parsed.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "vra.database.url must not embed credentials"
            );
        }

        return url;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "vra.database." + name + " must not be blank"
            );
        }

        return value;
    }
}
