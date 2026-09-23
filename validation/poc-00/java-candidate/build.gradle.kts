plugins {
    java
    id("org.springframework.boot") version "3.5.16"
}

group = "dev.vra.poc00"
version = "0.0.1-validation"

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.main { resources.srcDir(rootProject.file("shared")) }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Docker 29 requires API >= 1.44; used only by the test Docker client.
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
    mainClass.set("dev.vra.poc00.infrastructure.MigrateLocal")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
springBoot { mainClass.set("dev.vra.poc00.PocApplication") }
