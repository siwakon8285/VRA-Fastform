plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:4.1.1")
    )

    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    mainClass.set("dev.vra.VraApplication")
}
