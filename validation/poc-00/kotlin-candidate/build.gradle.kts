import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    id("org.springframework.boot") version "3.5.16"
}

group = "dev.vra.poc00.kotlin"
version = "0.0.1-validation"

repositories { mavenCentral() }

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    // Spring's Kotlin support requires reflect and Jackson's Kotlin constructor/parameter support.
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.4.20")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.main { resources.srcDir(rootProject.file("shared")) }

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { javaParameters.set(true) }
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Same Docker 29 API setting as Candidate A, for the test Docker client only.
    systemProperty("api.version", "1.44")
    testLogging { events("passed", "skipped", "failed") }
}
tasks.test { useJUnitPlatform { excludeTags("postgres") } }

val integrationTest by tasks.registering(Test::class) {
    description = "Real PostgreSQL migration, persistence and HTTP integration tests (requires Docker)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("postgres") }
    shouldRunAfter(tasks.test)
}
tasks.check { dependsOn(integrationTest) }

tasks.register<JavaExec>("migrateLocal") {
    description = "Explicit Flyway migration of the dedicated local POC database."
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.vra.poc00.kotlin.infrastructure.MigrateLocal")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

springBoot { mainClass.set("dev.vra.poc00.kotlin.PocApplicationKt") }
