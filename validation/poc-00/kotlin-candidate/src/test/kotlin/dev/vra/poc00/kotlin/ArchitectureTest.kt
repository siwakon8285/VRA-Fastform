package dev.vra.poc00.kotlin

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArchitectureTest {
    @Test
    fun kotlinProductionPackagesPointInward() {
        val classes: JavaClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("dev.vra.poc00.kotlin")

        // Prove that the bytecode importer saw Kotlin classes in the intended package tree.
        assertThat(classes.any { it.name == "dev.vra.poc00.kotlin.domain.Money" }).isTrue()
        assertThat(classes.any { it.name == "dev.vra.poc00.kotlin.application.ReservationApplicationService" }).isTrue()

        noClasses().that().resideInAPackage("..kotlin.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..", "jakarta.servlet..", "java.sql..",
                "..kotlin.interfaces..", "..kotlin.infrastructure..", "..kotlin.application.."
            ).check(classes)

        noClasses().that().resideInAnyPackage("..kotlin.application..", "..kotlin.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..kotlin.interfaces..").check(classes)
        noClasses().that().resideInAPackage("..kotlin.application..")
            .should().dependOnClassesThat().resideInAPackage("..kotlin.infrastructure..").check(classes)
        noClasses().that().resideInAPackage("..kotlin.interfaces..")
            .should().dependOnClassesThat().resideInAPackage("..kotlin.infrastructure..").check(classes)
    }
}
