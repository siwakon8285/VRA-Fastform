package dev.vra.poc00.kotlin

import dev.vra.poc00.kotlin.domain.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency
import java.util.UUID

@Tag("postgres")
@Testcontainers
class PostgresIntegrationTest {
    @BeforeEach
    fun cleanDisposableContainer() {
        jdbc = JdbcTemplate(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password))
        flyway().clean()
    }

    private lateinit var jdbc: JdbcTemplate

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
        if (target != null) configuration.target(MigrationVersion.fromVersion(target))
        return configuration.load()
    }

    private fun insertOrder(id: UUID, state: OrderState, created: Instant, confirmed: Instant?) {
        jdbc.update(
            "INSERT INTO orders(id,state,created_at,confirmed_at) VALUES (?,?,?,?)",
            id, state.name, OffsetDateTime.ofInstant(created, ZoneOffset.UTC),
            confirmed?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        )
    }

    private fun insertItem(order: UUID, sku: UUID, quantity: Int, price: BigDecimal) {
        jdbc.update(
            """INSERT INTO order_items(order_id,line_number,sku_id,product_name_snapshot,quantity,unit_price,currency)
                VALUES (?,1,?,'Synthetic tea',?,?,'THB')""",
            order, sku, quantity, price
        )
    }

    @Test
    fun latestMigrationAndInventoryRoundTrip() {
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(2)
        flyway().validate()
        val sku = UUID.randomUUID()
        jdbc.update("INSERT INTO inventory_balance(sku_id,on_hand,reserved) VALUES (?,10,3)", sku)
        val balance = jdbc.queryForObject(
            "SELECT * FROM inventory_balance WHERE sku_id=?",
            { rs, _ ->
                InventoryBalance(
                    SkuId(rs.getObject("sku_id", UUID::class.java)),
                    rs.getLong("on_hand"), rs.getLong("reserved"), rs.getLong("version")
                )
            }, sku
        )
        assertThat(balance).isEqualTo(InventoryBalance(SkuId(sku), 10, 3, 0))
        assertThat(balance!!.available).isEqualTo(7)
    }

    @Test
    fun populatedV1ToV2MigrationPreservesRowsNullAndChecksums() {
        assertThat(flyway("1").migrate().migrationsExecuted).isEqualTo(1)
        val order = UUID.randomUUID()
        val sku = UUID.randomUUID()
        val created = Instant.parse("2026-01-01T12:34:56.123456Z")
        insertOrder(order, OrderState.PENDING_PAYMENT, created, null)
        insertItem(order, sku, 2, BigDecimal("123.456789"))
        jdbc.update("INSERT INTO inventory_balance(sku_id,on_hand,reserved) VALUES (?,10,2)", sku)

        assertThat(jdbc.queryForObject(
            """SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='orders' AND column_name='cancellation_reason'""",
            Int::class.javaObjectType
        )!!).isZero()

        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1)
        flyway().validate()
        val history = jdbc.queryForList(
            "SELECT version, success, checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank"
        )
        assertThat(history).hasSize(2)
        assertThat(history.map { it["success"] }).containsOnly(true)
        val checksums = history.map { it["checksum"] }

        assertThat(jdbc.queryForObject("SELECT state FROM orders WHERE id=?", String::class.java, order))
            .isEqualTo("PENDING_PAYMENT")
        assertThat(jdbc.queryForObject(
            "SELECT created_at FROM orders WHERE id=?", OffsetDateTime::class.java, order
        )!!.toInstant()).isEqualTo(created)
        assertThat(jdbc.queryForObject(
            "SELECT cancellation_reason FROM orders WHERE id=?", String::class.java, order
        )).isNull()
        assertThat(jdbc.queryForObject(
            "SELECT unit_price FROM order_items WHERE order_id=?", BigDecimal::class.java, order
        )).isEqualByComparingTo("123.456789")
        assertThat(jdbc.queryForObject(
            "SELECT product_name_snapshot FROM order_items WHERE order_id=?", String::class.java, order
        )).isEqualTo("Synthetic tea")
        assertThat(jdbc.queryForObject(
            "SELECT quantity FROM order_items WHERE order_id=?", Int::class.javaObjectType, order
        )!!).isEqualTo(2)
        assertThat(jdbc.queryForObject(
            "SELECT reserved FROM inventory_balance WHERE sku_id=?", Long::class.javaObjectType, sku
        )!!).isEqualTo(2)

        jdbc.update("UPDATE orders SET state='CANCELLED', cancellation_reason='Synthetic cancellation' WHERE id=?", order)
        assertThat(jdbc.queryForObject(
            "SELECT cancellation_reason FROM orders WHERE id=?", String::class.java, order
        )).isEqualTo("Synthetic cancellation")
        flyway().validate()
        assertThat(jdbc.queryForList(
            "SELECT version, success, checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank"
        ).map { it["checksum"] }).isEqualTo(checksums)
    }

    @Test
    fun orderMoneyCurrencyStateTimestampAndNullableReasonRoundTrip() {
        flyway().migrate()
        val id = UUID.randomUUID()
        val sku = UUID.randomUUID()
        val created = Instant.parse("2026-01-01T01:02:03.123456Z")
        val confirmed = created.plusSeconds(60)
        insertOrder(id, OrderState.CONFIRMED, created, confirmed)
        insertItem(id, sku, 2, BigDecimal("1234567890.123456789"))

        val item = jdbc.queryForObject("SELECT * FROM order_items WHERE order_id=?", { rs, _ ->
            OrderItem(
                SkuId(rs.getObject("sku_id", UUID::class.java)),
                rs.getString("product_name_snapshot"),
                rs.getInt("quantity"),
                Money(rs.getBigDecimal("unit_price"), Currency.getInstance(rs.getString("currency")))
            )
        }, id)!!

        val loaded = jdbc.queryForObject("SELECT * FROM orders WHERE id=?", { rs, _ ->
            Order(
                OrderId(rs.getObject("id", UUID::class.java)),
                OrderState.valueOf(rs.getString("state")),
                rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                rs.getObject("confirmed_at", OffsetDateTime::class.java)?.toInstant(),
                rs.getString("cancellation_reason"),
                listOf(item),
                rs.getLong("version")
            )
        }, id)!!

        assertThat(loaded.state).isEqualTo(OrderState.CONFIRMED)
        assertThat(loaded.createdAt).isEqualTo(created)
        assertThat(loaded.confirmedAt).isEqualTo(confirmed)
        assertThat(loaded.cancellationReason).isNull()
        assertThat(loaded.items.single()).isEqualTo(
            OrderItem(
                SkuId(sku), "Synthetic tea", 2,
                Money(BigDecimal("1234567890.123456789"), Currency.getInstance("THB"))
            )
        )
    }

    @Test
    fun postgresConstraintsRejectInvalidInventoryAndItemQuantity() {
        flyway().migrate()
        for (values in listOf(-1L to 0L, 1L to -1L, 1L to 2L)) {
            assertThatThrownBy {
                jdbc.update(
                    "INSERT INTO inventory_balance(sku_id,on_hand,reserved) VALUES (?,?,?)",
                    UUID.randomUUID(), values.first, values.second
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }
        val order = UUID.randomUUID()
        insertOrder(order, OrderState.CREATED, Instant.now(), null)
        for (quantity in listOf(0, -1)) {
            assertThatThrownBy { insertItem(order, UUID.randomUUID(), quantity, BigDecimal.ONE) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgresTestSupport.postgres()
    }
}
