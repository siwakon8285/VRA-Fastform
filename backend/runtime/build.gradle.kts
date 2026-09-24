plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:4.1.1")
    )

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation(project(":migration"))
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    mainClass.set("dev.vra.VraApplication")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("postgres")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Real PostgreSQL runtime persistence and transaction verification."
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
