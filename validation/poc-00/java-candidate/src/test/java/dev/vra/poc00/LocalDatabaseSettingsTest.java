package dev.vra.poc00;

import dev.vra.poc00.infrastructure.LocalDatabaseSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LocalDatabaseSettingsTest {
    @Test void missingPasswordFailsClearlyWithoutFallback() {
        assertThatThrownBy(() -> new LocalDatabaseSettings(LocalDatabaseSettings.DEFAULT_URL, "vra_poc00", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("VRA_DB_PASSWORD is required");
    }
    @Test void nonPocAndRemoteTargetsAreRejected() {
        for (String url : new String[] {"jdbc:postgresql://remote:55432/vra_poc00",
                "jdbc:postgresql://127.0.0.1:55432/other", LocalDatabaseSettings.DEFAULT_URL + "?password=secret"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new LocalDatabaseSettings(url, "vra_poc00", "synthetic"));
        }
    }
}
