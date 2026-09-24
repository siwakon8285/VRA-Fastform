package dev.vra.platform.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vra.database")
public record RuntimeDatabaseProperties(
        String url,
        String username,
        String password
) {

    public RuntimeDatabaseProperties {
        url = requireNonBlank(url, "url");
        username = requireNonBlank(username, "username");
        password = requireNonBlank(password, "password");
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
