import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("org.springframework.boot") version "4.1.1" apply false
}

allprojects {
    group = "dev.vra"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()

            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "9.7.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum =
        "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
}
