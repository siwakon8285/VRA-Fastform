package dev.vra.async.architecture;

import java.net.http.HttpClient;
import java.util.List;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import dev.vra.VraApplication;
import dev.vra.async.adapter.out.producer.JdbcReservationOutboxPublisher;
import dev.vra.async.bootstrap.ApiMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

@AnalyzeClasses(packages = "dev.vra", importOptions = ImportOption.DoNotIncludeTests.class)
class AsyncArchitectureTest {
    @ArchTest static final ArchRule async_contract_and_inventory_domain_are_inner = noClasses()
            .that().resideInAnyPackage("dev.vra.async.contract..", "dev.vra.inventory.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.vra.async.application..",
                    "dev.vra.async.adapter..", "dev.vra.async.bootstrap..", "dev.vra.inventory.adapter..",
                    "dev.vra.platform..", "org.springframework..", "jakarta.persistence..",
                    "java.sql..", "javax.sql..");

    @ArchTest static final ArchRule async_application_has_no_outer_or_web_dependency = noClasses()
            .that().resideInAPackage("dev.vra.async.application..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.vra.async.adapter..",
                    "dev.vra.async.bootstrap..", "dev.vra.inventory.adapter..", "dev.vra.platform.web..",
                    "org.springframework.web..", "java.sql..", "javax.sql..", "jakarta.persistence..");

    // ReconciliationPolicy legitimately shares the accepted JitterSource application contract.
    @ArchTest static final ArchRule reconciliation_has_no_delivery_adapter_or_bootstrap_dependency = noClasses()
            .that().resideInAPackage("dev.vra.async.application.reconciliation..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.vra.async.adapter.out.delivery..",
                    "dev.vra.async.bootstrap..",
                    "dev.vra.inventory..", "org.springframework.web..");

    @ArchTest static final ArchRule async_adapters_do_not_depend_on_bootstrap_or_controllers = noClasses()
            .that().resideInAPackage("dev.vra.async.adapter..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.vra.async.bootstrap..",
                    "dev.vra.inventory.adapter.in.web..", "dev.vra.platform.web..");

    @ArchTest static final ArchRule worker_bootstrap_does_not_import_api_business_stack = noClasses()
            .that().resideInAPackage("dev.vra.async.bootstrap..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.vra.inventory.adapter.in.web..",
                    "dev.vra.inventory.application..", "dev.vra.platform.web..");

    @ArchTest static final ArchRule reconciler_mode_does_not_compose_delivery_or_api_stack = noClasses()
            .that().haveNameMatching("dev\\.vra\\.async\\.bootstrap\\.ReconciliationWorkerMode(\\$.*)?")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "dev.vra.async.application.delivery..", "dev.vra.async.application.projection..",
                    "dev.vra.async.adapter.out.delivery..", "dev.vra.async.adapter.out.projection..",
                    "dev.vra.async.adapter.out.producer..", "dev.vra.inventory..", "dev.vra.platform.web..")
            .orShould().dependOnClassesThat().haveNameMatching(
                    "dev\\.vra\\.async\\.bootstrap\\.(OutboxWorkerMode|OutboxWorkerLoop|ApiMode)(\\$.*)?");

    @ArchTest static final ArchRule http_client_is_only_in_validation_external_adapter = noClasses()
            .that().resideOutsideOfPackage("dev.vra.async.adapter.out.external..")
            .should().dependOnClassesThat().areAssignableTo(HttpClient.class);

    @Test void rootApiScanAndExplicitProducerImportExcludeWorkerAndReconciler() {
        SpringBootApplication root = VraApplication.class.getAnnotation(SpringBootApplication.class);
        assertNotNull(root);
        assertEquals(List.of("dev.vra.inventory", "dev.vra.platform"),
                List.of(root.scanBasePackages()));
        Import apiImport = VraApplication.class.getAnnotation(Import.class);
        assertNotNull(apiImport);
        assertEquals(List.of(ApiMode.class), List.of(apiImport.value()));
        ComponentScan producerScan = ApiMode.class.getAnnotation(ComponentScan.class);
        assertNotNull(producerScan);
        assertEquals(List.of(JdbcReservationOutboxPublisher.class),
                List.of(producerScan.basePackageClasses()));
    }

    @Test void asyncLayersKeepPortsPoliciesAdaptersAndProcessCompositionInTheirPackages() {
        for (Class<?> portOrPolicy : List.of(
                dev.vra.async.application.delivery.DeliveryPort.class,
                dev.vra.async.application.delivery.DeliveryRetryPolicy.class,
                dev.vra.async.application.reconciliation.ReconciliationPort.class,
                dev.vra.async.application.reconciliation.ReconciliationPolicy.class,
                dev.vra.async.application.external.ExternalEffectPort.class)) {
            assertTrue(portOrPolicy.getPackageName().startsWith("dev.vra.async.application."));
        }
        for (Class<?> adapter : List.of(
                dev.vra.async.adapter.out.delivery.JdbcDeliveryRepository.class,
                dev.vra.async.adapter.out.reconciliation.JdbcReconciliationRepository.class,
                dev.vra.async.adapter.out.external.ValidationSimulatorHttpAdapter.class,
                dev.vra.async.adapter.out.visibility.JdbcAsyncVisibility.class)) {
            assertTrue(adapter.getPackageName().startsWith("dev.vra.async.adapter.out."));
        }
        for (Class<?> mode : List.of(dev.vra.async.bootstrap.OutboxWorkerMode.class,
                dev.vra.async.bootstrap.ReconciliationWorkerMode.class,
                dev.vra.async.bootstrap.ReconciliationLoop.class,
                dev.vra.async.bootstrap.OutboxWorkerLoop.class)) {
            assertEquals("dev.vra.async.bootstrap", mode.getPackageName());
        }
    }
}
