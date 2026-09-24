package dev.vra.platform.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
