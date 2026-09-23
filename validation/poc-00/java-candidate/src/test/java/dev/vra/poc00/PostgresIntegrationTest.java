package dev.vra.poc00;

import dev.vra.poc00.domain.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("postgres")
@Testcontainers
class PostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestSupport.postgres();
    private JdbcTemplate jdbc;
    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) configuration.target(target);
        return configuration.load();
    }
    @BeforeEach void emptyDedicatedContainerSchema() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway(null).clean(); // Only this disposable Testcontainer; never local/shared infrastructure.
    }
    private void insertOrder(UUID id, OrderState state, Instant created, Instant confirmed) {
        jdbc.update("INSERT INTO orders(id,state,created_at,confirmed_at) VALUES (?,?,?,?)",
                id, state.name(), OffsetDateTime.ofInstant(created, java.time.ZoneOffset.UTC),
                confirmed == null ? null : OffsetDateTime.ofInstant(confirmed, java.time.ZoneOffset.UTC));
    }
    private void insertItem(UUID order, UUID sku, int quantity, BigDecimal price) {
        jdbc.update("""
                INSERT INTO order_items(order_id,line_number,sku_id,product_name_snapshot,quantity,unit_price,currency)
                VALUES (?,1,?,'Synthetic tea',?,?,'THB')
                """, order, sku, quantity, price);
    }

    @Test void latestMigrationAndInventoryRoundTrip() {
        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(2);
        flyway(null).validate();
        var sku = UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_balance(sku_id,on_hand,reserved) VALUES (?,10,3)", sku);
        var balance = jdbc.queryForObject("SELECT * FROM inventory_balance WHERE sku_id=?", (rs, row) ->
                new InventoryBalance(new SkuId(rs.getObject("sku_id", UUID.class)), rs.getLong("on_hand"),
                        rs.getLong("reserved"), rs.getLong("version")), sku);
        assertThat(balance).isEqualTo(new InventoryBalance(new SkuId(sku), 10, 3, 0));
        assertThat(balance.available()).isEqualTo(7);
    }

    @Test void populatedV1ToV2PreservesAllRepresentativeData() {
        assertThat(flyway("1").migrate().migrationsExecuted).isEqualTo(1);
        var order = UUID.randomUUID();
        var sku = UUID.randomUUID();
        var created = Instant.parse("2026-01-01T12:34:56.123456Z");
        insertOrder(order, OrderState.PENDING_PAYMENT, created, null);
        insertItem(order, sku, 2, new BigDecimal("123.456789"));
        jdbc.update("INSERT INTO inventory_balance(sku_id,on_hand,reserved) VALUES (?,10,2)", sku);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='orders' AND column_name='cancellation_reason'
                """, Integer.class)).isZero();
        assertThat(flyway(null).migrate().migrationsExecuted).isEqualTo(1);
        flyway(null).validate();
        assertThat(flyway(null).info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(jdbc.queryForObject("SELECT state FROM orders WHERE id=?", String.class, order)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT created_at FROM orders WHERE id=?", OffsetDateTime.class, order).toInstant()).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM orders WHERE id=?", String.class, order)).isNull();
        assertThat(jdbc.queryForObject("SELECT unit_price FROM order_items WHERE order_id=?", BigDecimal.class, order))
                .isEqualByComparingTo("123.456789");
        assertThat(jdbc.queryForObject("SELECT quantity FROM order_items WHERE order_id=?", Integer.class, order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balance WHERE sku_id=?", Long.class, sku)).isEqualTo(2);
        jdbc.update("UPDATE orders SET state='CANCELLED', cancellation_reason='Synthetic cancellation' WHERE id=?", order);
        assertThat(jdbc.queryForObject("SELECT cancellation_reason FROM orders WHERE id=?", String.class, order)).isEqualTo("Synthetic cancellation");
    }

    @Test void orderMoneyCurrencyStateTimestampAndNullabilityRoundTrip() {
        flyway(null).migrate();
        var id = UUID.randomUUID();
        var sku = UUID.randomUUID();
        var created = Instant.parse("2026-01-01T01:02:03.123456Z");
        var confirmed = created.plusSeconds(60);
        insertOrder(id, OrderState.CONFIRMED, created, confirmed);
        insertItem(id, sku, 2, new BigDecimal("1234567890.123456789"));
        OrderItem item = jdbc.queryForObject("SELECT * FROM order_items WHERE order_id=?", (rs, row) ->
                new OrderItem(new SkuId(rs.getObject("sku_id", UUID.class)), rs.getString("product_name_snapshot"),
                        rs.getInt("quantity"), new Money(rs.getBigDecimal("unit_price"),
                        Currency.getInstance(rs.getString("currency")))), id);
        Order loaded = jdbc.queryForObject("SELECT * FROM orders WHERE id=?", (rs, row) ->
                new Order(new OrderId(rs.getObject("id", UUID.class)), OrderState.valueOf(rs.getString("state")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        Optional.ofNullable(rs.getObject("confirmed_at", OffsetDateTime.class)).map(OffsetDateTime::toInstant),
                        Optional.ofNullable(rs.getString("cancellation_reason")), List.of(item), rs.getLong("version")), id);
        assertThat(loaded.state()).isEqualTo(OrderState.CONFIRMED);
        assertThat(loaded.createdAt()).isEqualTo(created);
        assertThat(loaded.confirmedAt()).contains(confirmed);
        assertThat(loaded.cancellationReason()).isEmpty();
        assertThat(loaded.items().getFirst()).isEqualTo(new OrderItem(new SkuId(sku), "Synthetic tea", 2,
                new Money(new BigDecimal("1234567890.123456789"), Currency.getInstance("THB"))));
    }

    @Test void databaseRejectsInvalidInventoryAndOrderItemQuantity() {
        flyway(null).migrate();
        for (long[] balance : new long[][] {{-1, 0}, {1, -1}, {1, 2}}) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO inventory_balance(sku_id,on_hand,reserved) VALUES (?,?,?)",
                    UUID.randomUUID(), balance[0], balance[1])).isInstanceOf(DataIntegrityViolationException.class);
        }
        var order = UUID.randomUUID();
        insertOrder(order, OrderState.CREATED, Instant.now(), null);
        for (int quantity : new int[] {0, -1})
            assertThatThrownBy(() -> insertItem(order, UUID.randomUUID(), quantity, BigDecimal.ONE))
                    .isInstanceOf(DataIntegrityViolationException.class);
    }
}
