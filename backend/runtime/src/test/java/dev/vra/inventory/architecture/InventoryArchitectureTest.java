package dev.vra.inventory.architecture;

import jakarta.persistence.Entity;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "dev.vra",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class InventoryArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_and_outer_layer_independent =
            noClasses()
                    .that()
                    .resideInAPackage("dev.vra.inventory.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "dev.vra.inventory.application..",
                            "dev.vra.inventory.adapter..",
                            "dev.vra.platform..",
                            "org.springframework..",
                            "jakarta.persistence..",
                            "java.sql..",
                            "javax.sql.."
                    );

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters =
            noClasses()
                    .that()
                    .resideInAPackage("dev.vra.inventory.application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("dev.vra.inventory.adapter..");

    @ArchTest
    static final ArchRule jpa_entities_stay_in_persistence_adapter =
            classes()
                    .that()
                    .areAnnotatedWith(Entity.class)
                    .should()
                    .resideInAPackage(
                            "dev.vra.inventory.adapter.out.persistence.."
                    );
}
