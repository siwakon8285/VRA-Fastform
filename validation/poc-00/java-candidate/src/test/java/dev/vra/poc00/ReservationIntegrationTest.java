package dev.vra.poc00;

import dev.vra.poc00.application.ReservationApplicationService;
import dev.vra.poc00.domain.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Tag("postgres")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestSupport.postgres();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        // Explicit test setup, not application-startup migration.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        // Testcontainers appends loggerLevel=OFF; runtime URLs deliberately disallow arbitrary parameters.
        registry.add("VRA_DB_URL", () -> "jdbc:postgresql://" + POSTGRES.getHost()
                + ":" + POSTGRES.getMappedPort(5432) + "/vra_poc00");
        registry.add("VRA_DB_USERNAME", POSTGRES::getUsername);
        registry.add("VRA_DB_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationApplicationService service;
    @Autowired PlatformTransactionManager transactions;
    private SkuId sku;

    @BeforeEach void inventory() {
        sku = new SkuId(UUID.randomUUID());
        jdbc.update("INSERT INTO inventory_balance(sku_id,on_hand,reserved) VALUES (?,5,0)", sku.value());
    }

    @Test void actualHttpToPostgresSuccessAndRejections() {
        var response = http.postForEntity("/poc/reservations", Map.of("skuId", sku.value(), "quantity", 2), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("reserved", 2).containsEntry("available", 3);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        var insufficient = http.postForEntity("/poc/reservations", Map.of("skuId", sku.value(), "quantity", 4), Map.class);
        assertThat(insufficient.getStatusCode().value()).isEqualTo(409);
        assertThat(insufficient.getBody()).containsEntry("code", "inventory.insufficient_stock");
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balance WHERE sku_id=?", Long.class, sku.value())).isEqualTo(2);
        var unknown = http.postForEntity("/poc/reservations", Map.of("skuId", UUID.randomUUID(), "quantity", 1), Map.class);
        assertThat(unknown.getStatusCode().value()).isEqualTo(404);
        for (int quantity : new int[] {0, -1}) {
            var invalid = http.postForEntity("/poc/reservations", Map.of("skuId", sku.value(), "quantity", quantity), Map.class);
            assertThat(invalid.getStatusCode().value()).isEqualTo(400);
            assertThat(invalid.getBody()).containsEntry("code", "request.invalid");
        }
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode().value()).isEqualTo(200);
    }

    @Test void applicationTransactionParticipatesAndRollsBackEarlierMutation() {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.reserve(sku, 2);
            service.reserve(sku, 4); // Fails after the first mutation within the same transaction.
        })).isInstanceOf(DomainFailure.InsufficientStock.class);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balance WHERE sku_id=?", Long.class, sku.value())).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM inventory_balance WHERE sku_id=?", Long.class, sku.value())).isZero();
    }

    @Test void configuredJsonValidationRejectsFractionalNullAndUnknownFields() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (String body : new String[] {
                "{\"skuId\":\"" + sku.value() + "\",\"quantity\":1.5}",
                "{\"skuId\":\"" + sku.value() + "\",\"quantity\":null}",
                "{\"skuId\":\"" + sku.value() + "\",\"quantity\":1,\"extra\":true}"}) {
            var response = http.postForEntity("/poc/reservations", new HttpEntity<>(body, headers), Map.class);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("code", "request.invalid");
        }
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balance WHERE sku_id=?", Long.class, sku.value())).isZero();
    }

    @Test void servletErrorFallbackIsSafeAndCorrelated() {
        var response = http.getForEntity("/error", Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("code", "internal.error")
                .containsEntry("message", "Internal server error.")
                .containsEntry("requestId", response.getHeaders().getFirst("X-Request-Id"))
                .doesNotContainKeys("exception", "trace", "path");
    }
}
