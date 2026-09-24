package dev.vra.poc00;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test void dependenciesPointInward() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("dev.vra.poc00");
        noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.servlet..", "java.sql..",
                        "..interfaces..", "..infrastructure..", "..application..").check(classes);
        noClasses().that().resideInAnyPackage("..application..", "..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..interfaces..").check(classes);
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..").check(classes);
        noClasses().that().resideInAPackage("..interfaces..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..").check(classes);
    }
}
