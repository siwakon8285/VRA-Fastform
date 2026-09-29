package dev.vra.platform.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDatabasePropertiesTest {

    @Test
    void acceptsCompleteConfiguration() {
        var properties = new RuntimeDatabaseProperties(
                "jdbc:postgresql://localhost:5432/vra",
                "vra_runtime",
                "secret"
        );

        assertEquals("vra_runtime", properties.username());
    }

    @Test
    void rejectsBlankUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeDatabaseProperties(
                        " ",
                        "vra_runtime",
                        "secret"
                )
        );
    }

    @Test
    void rejectsNonPostgresJdbcUrl() {
        var error = assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeDatabaseProperties(
                        "jdbc:mysql://localhost:3306/vra",
                        "vra_runtime",
                        "secret"
                )
        );

        assertTrue(
                error.getMessage().contains("jdbc:postgresql://")
        );
    }

    @Test
    void rejectsMalformedPostgresJdbcUrl() {
        var error = assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeDatabaseProperties(
                        "jdbc:postgresql://[broken",
                        "vra_runtime",
                        "secret"
                )
        );

        assertEquals(
                "vra.database.url must be a valid PostgreSQL JDBC URL",
                error.getMessage()
        );
    }

    @Test
    void rejectsPostgresJdbcUrlWithoutHost() {
        var error = assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeDatabaseProperties(
                        "jdbc:postgresql:///vra",
                        "vra_runtime",
                        "secret"
                )
        );

        assertEquals(
                "vra.database.url must include a PostgreSQL host",
                error.getMessage()
        );
    }

    @Test
    void rejectsPostgresJdbcUrlWithoutDatabaseName() {
        var error = assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeDatabaseProperties(
                        "jdbc:postgresql://localhost:5432",
                        "vra_runtime",
                        "secret"
                )
        );

        assertEquals(
                "vra.database.url must include a database name",
                error.getMessage()
        );
    }

    @Test
    void rejectsCredentialsEmbeddedInDatabaseUrl() {
        var error = assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeDatabaseProperties(
                        "jdbc:postgresql://user:secret@localhost:5432/vra",
                        "vra_runtime",
                        "secret"
                )
        );

        assertEquals(
                "vra.database.url must not embed credentials",
                error.getMessage()
        );
    }

    @Test
    void rejectsBlankUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeDatabaseProperties(
                        "jdbc:postgresql://localhost:5432/vra",
                        " ",
                        "secret"
                )
        );
    }

    @Test
    void rejectsBlankPassword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeDatabaseProperties(
                        "jdbc:postgresql://localhost:5432/vra",
                        "vra_runtime",
                        " "
                )
        );
    }
}
