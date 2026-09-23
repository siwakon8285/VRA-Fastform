package dev.vra.poc00.kotlin

import dev.vra.poc00.kotlin.application.ReservationApplicationService
import dev.vra.poc00.kotlin.domain.DomainFailure
import dev.vra.poc00.kotlin.domain.SkuId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Tag("postgres")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationIntegrationTest {
    @Autowired
    lateinit var http: TestRestTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var service: ReservationApplicationService

    @Autowired
    lateinit var transactions: PlatformTransactionManager

    private var sku = SkuId(UUID.randomUUID())

    @BeforeEach
    fun seedBalance() {
        sku = SkuId(UUID.randomUUID())
        jdbc.update("INSERT INTO inventory_balance(sku_id,on_hand,reserved) VALUES (?,5,0)", sku.value)
    }

    @Test
    fun realHttpPostgresSuccessInsufficientUnknownInvalidAndHealth() {
        val success = http.postForEntity(
            "/poc/reservations", mapOf("skuId" to sku.value, "quantity" to 2), Map::class.java
        )
        assertThat(success.statusCode.value()).isEqualTo(200)
        assertThat(success.body!!["reserved"]).isEqualTo(2)
        assertThat(success.body!!["available"]).isEqualTo(3)
        assertThat(success.headers.getFirst("X-Request-Id")).isNotBlank()

        val insufficient = http.postForEntity(
            "/poc/reservations", mapOf("skuId" to sku.value, "quantity" to 4), Map::class.java
        )
        assertThat(insufficient.statusCode.value()).isEqualTo(409)
        assertThat(insufficient.body!!["code"]).isEqualTo("inventory.insufficient_stock")

        val unknown = http.postForEntity(
            "/poc/reservations", mapOf("skuId" to UUID.randomUUID(), "quantity" to 1), Map::class.java
        )
        assertThat(unknown.statusCode.value()).isEqualTo(404)
        assertThat(unknown.body!!["code"]).isEqualTo("inventory.unknown_sku")

        for (quantity in listOf(0, -1)) {
            val invalid = http.postForEntity(
                "/poc/reservations", mapOf("skuId" to sku.value, "quantity" to quantity), Map::class.java
            )
            assertThat(invalid.statusCode.value()).isEqualTo(400)
            assertThat(invalid.body!!["code"]).isEqualTo("request.invalid")
        }
        assertThat(jdbc.queryForObject(
            "SELECT reserved FROM inventory_balance WHERE sku_id=?", Long::class.javaObjectType, sku.value
        )!!).isEqualTo(2)
        assertThat(http.getForEntity("/actuator/health", String::class.java).statusCode.value()).isEqualTo(200)
    }

    @Test
    fun fractionalNullAndUnknownFieldsAreSafeAndDoNotMutate() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val badJson = listOf(
            """{"skuId":"${sku.value}","quantity":1.5}""",
            """{"skuId":"${sku.value}","quantity":null}""",
            """{"skuId":"${sku.value}","quantity":1,"extra":true}"""
        )
        for (json in badJson) {
            val response = http.postForEntity(
                "/poc/reservations", HttpEntity(json, headers), Map::class.java
            )
            assertThat(response.statusCode.value()).isEqualTo(400)
            assertThat(response.body!!["code"]).isEqualTo("request.invalid")
        }
        assertThat(jdbc.queryForObject(
            "SELECT reserved FROM inventory_balance WHERE sku_id=?", Long::class.javaObjectType, sku.value
        )!!).isZero()
    }

    @Test
    fun publicSpringTransactionProxyRollsBackEarlierReservationOnFailure() {
        assertThat(AopUtils.isAopProxy(service)).isTrue()
        assertThatThrownBy {
            TransactionTemplate(transactions).executeWithoutResult {
                service.reserve(sku, 2)
                service.reserve(sku, 4)
            }
        }.isInstanceOf(DomainFailure.InsufficientStock::class.java)

        assertThat(jdbc.queryForObject(
            "SELECT reserved FROM inventory_balance WHERE sku_id=?", Long::class.javaObjectType, sku.value
        )!!).isZero()
        assertThat(jdbc.queryForObject(
            "SELECT version FROM inventory_balance WHERE sku_id=?", Long::class.javaObjectType, sku.value
        )!!).isZero()
    }

    @Test
    fun servletErrorFallbackIsSafeAndCorrelated() {
        val response = http.getForEntity("/error", Map::class.java)
        assertThat(response.statusCode.value()).isEqualTo(500)
        val body = response.body!!
        assertThat(body["code"]).isEqualTo("internal.error")
        assertThat(body["message"]).isEqualTo("Internal server error.")
        assertThat(body["requestId"]).isEqualTo(response.headers.getFirst("X-Request-Id"))
        assertThat(body.keys).doesNotContain("exception", "trace", "path")
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgresTestSupport.postgres()

        @DynamicPropertySource
        @JvmStatic
        fun database(registry: DynamicPropertyRegistry) {
            // Same V1/V2 resources and isolated postgres:17.11; never the persistent Compose database.
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            registry.add("VRA_DB_URL") {
                "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/vra_poc00"
            }
            registry.add("VRA_DB_USERNAME") { postgres.username }
            registry.add("VRA_DB_PASSWORD") { postgres.password }
        }
    }
}
