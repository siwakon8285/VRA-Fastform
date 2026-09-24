plugins {
    java
    application
    id("org.springframework.boot")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:4.1.1")
    )

    implementation("org.flywaydb:flyway-core")

    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(
        platform("org.springframework.boot:spring-boot-dependencies:4.1.1")
    )
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("dev.vra.migration.MigrationMain")
}

springBoot {
    mainClass.set("dev.vra.migration.MigrationMain")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("postgres")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Real PostgreSQL migration and database privilege verification."
    group = "verification"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    useJUnitPlatform {
        includeTags("postgres")
    }

    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}
