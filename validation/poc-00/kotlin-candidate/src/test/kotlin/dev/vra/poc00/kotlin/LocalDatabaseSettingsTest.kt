package dev.vra.poc00.kotlin

import dev.vra.poc00.kotlin.infrastructure.LocalDatabaseSettings
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LocalDatabaseSettingsTest {
    @Test
    fun missingPasswordFailsClearly() {
        assertThatThrownBy {
            LocalDatabaseSettings(LocalDatabaseSettings.DEFAULT_URL, "vra_poc00", null)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("VRA_DB_PASSWORD is required")
    }

    @Test
    fun remoteOrNonPocTargetsAreRejected() {
        for (url in listOf(
            "jdbc:postgresql://remote:55432/vra_poc00",
            "jdbc:postgresql://127.0.0.1:55432/other",
            "${LocalDatabaseSettings.DEFAULT_URL}?password=literal"
        )) {
            assertThatIllegalArgumentException().isThrownBy {
                LocalDatabaseSettings(url, "vra_poc00", "test-only")
            }
        }
    }

    @Test
    fun nonPocUsernameIsRejected() {
        assertThatIllegalArgumentException().isThrownBy {
            LocalDatabaseSettings(LocalDatabaseSettings.DEFAULT_URL, "other_user", "test-only")
        }
    }

    @Test
    fun settingsDoNotExposePasswordThroughToString() {
        val settings = LocalDatabaseSettings(LocalDatabaseSettings.DEFAULT_URL, "vra_poc00", "test-only")
        org.assertj.core.api.Assertions.assertThat(settings.toString()).doesNotContain("test-only")
    }
}
