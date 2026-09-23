package dev.vra.poc00.kotlin

import dev.vra.poc00.kotlin.domain.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import java.util.UUID

class DomainTest {
    private val sku = SkuId(UUID.randomUUID())
    private val thb = Currency.getInstance("THB")

    private fun money(amount: String) = Money(BigDecimal(amount), thb)
    private fun item() = OrderItem(sku, "Synthetic tea", 2, money("12.50"))
    private fun order() = Order.create(
        OrderId(UUID.randomUUID()), Instant.parse("2026-01-01T00:00:00Z"), listOf(item())
    )

    @Test
    fun validMoneyIsExactAndScaleIndependentIncludingHash() {
        assertThat(money("12.50")).isEqualTo(money("12.500"))
        assertThat(money("12.50").hashCode()).isEqualTo(money("12.500").hashCode())
    }

    @Test
    fun negativeMoneyIsRejected() {
        assertThatThrownBy { money("-0.01") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun requiredMoneyArgumentsAreNonNullAndJavaBoundaryStillChecksThem() {
        val constructor = Money::class.java.getConstructor(BigDecimal::class.java, Currency::class.java)
        for (args in listOf(
            arrayOf<Any?>(null, thb),
            arrayOf<Any?>(BigDecimal.ONE, null)
        )) {
            assertThatThrownBy { constructor.newInstance(*args) }
                .isInstanceOf(InvocationTargetException::class.java)
                .hasCauseInstanceOf(NullPointerException::class.java)
        }
    }

    @Test
    fun sameCurrencyArithmeticIsExact() {
        assertThat(money("0.1").add(money("0.2"))).isEqualTo(money("0.3"))
    }

    @Test
    fun currencyMismatchIsTyped() {
        assertThatThrownBy {
            money("1").add(Money(BigDecimal.ONE, Currency.getInstance("USD")))
        }.isInstanceOf(DomainFailure.CurrencyMismatch::class.java)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun nonPositiveQuantityIsRejected(quantity: Int) {
        assertThatThrownBy { InventoryBalance(sku, 5, 0, 0).reserve(quantity) }
            .isInstanceOf(DomainFailure.InvalidQuantity::class.java)
        assertThatThrownBy {
            InventoryReservation(UUID.randomUUID(), sku, quantity, InventoryReservation.State.ACTIVE)
        }.isInstanceOf(DomainFailure.InvalidQuantity::class.java)
        assertThatThrownBy { OrderItem(sku, "Tea", quantity, money("1")) }
            .isInstanceOf(DomainFailure.InvalidQuantity::class.java)
    }

    @Test
    fun validBalanceAndAvailability() {
        val original = InventoryBalance(sku, 10, 3, 0)
        assertThat(original.available).isEqualTo(7)
        val reserved = original.reserve(7)
        assertThat(reserved.available).isZero()
        assertThat(original.reserved).isEqualTo(3)
        assertThatThrownBy { reserved.reserve(1) }
            .isInstanceOf(DomainFailure.InsufficientStock::class.java)
    }

    @Test
    fun invalidBalancesAreRejected() {
        assertThatThrownBy { InventoryBalance(sku, -1, 0, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { InventoryBalance(sku, 1, -1, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { InventoryBalance(sku, 1, 2, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { InventoryBalance(sku, 1, 0, -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun validTransitionsAndNullableStateSemantics() {
        val created = order()
        assertThat(created.confirmedAt).isNull()
        assertThat(created.cancellationReason).isNull()
        val confirmedAt = created.createdAt.plusSeconds(1)
        val confirmed = created.pendingPayment().confirm(confirmedAt)
        assertThat(confirmed.state).isEqualTo(OrderState.CONFIRMED)
        assertThat(confirmed.confirmedAt).isEqualTo(confirmedAt)
        assertThat(confirmed.cancellationReason).isNull()
        assertThat(created.state).isEqualTo(OrderState.CREATED)
    }

    @Test
    fun invalidTransitionIsRejected() {
        assertThatThrownBy { order().confirm(Instant.now()) }
            .isInstanceOf(DomainFailure.InvalidTransition::class.java)
    }

    @Test
    fun cancelledOrderCannotBeConfirmedAndLegacyReasonMayBeNull() {
        val cancelled = order().pendingPayment().cancel("Buyer requested cancellation")
        assertThat(cancelled.confirmedAt).isNull()
        assertThat(cancelled.cancellationReason).isEqualTo("Buyer requested cancellation")
        assertThatThrownBy { cancelled.confirm(Instant.now()) }
            .isInstanceOf(DomainFailure.InvalidTransition::class.java)

        val legacy = Order(
            cancelled.id, cancelled.state, cancelled.createdAt, null, null, cancelled.items, cancelled.version
        )
        assertThat(legacy.cancellationReason).isNull()
    }

    @Test
    fun transitionGraphIsExhaustive() {
        for (from in OrderState.entries) for (to in OrderState.entries) {
            val valid = (from == OrderState.CREATED && to == OrderState.PENDING_PAYMENT) ||
                (from == OrderState.PENDING_PAYMENT && to in setOf(OrderState.CONFIRMED, OrderState.CANCELLED))
            if (valid) assertThatCode { from.requireTransitionTo(to) }.doesNotThrowAnyException()
            else assertThatThrownBy { from.requireTransitionTo(to) }
                .isInstanceOf(DomainFailure.InvalidTransition::class.java)
        }
    }

    @Test
    fun idempotencyKeyRequiresNonBlankTextWithinLimit() {
        for (value in listOf("", "  ", "x".repeat(129))) {
            assertThatThrownBy { IdempotencyKey(value) }
                .isInstanceOf(DomainFailure.InvalidIdempotencyKey::class.java)
        }
        assertThat(IdempotencyKey("x".repeat(128)).value).hasSize(128)
    }

    @Test
    fun orderDefensivelyCopiesHistoricalItemCollection() {
        val callerItems = mutableListOf(item())
        val snapshot = Order.create(OrderId(UUID.randomUUID()), Instant.now(), callerItems)
        callerItems[0] = OrderItem(sku, "Changed tea", 1, money("99"))
        assertThat(snapshot.items.single()).isEqualTo(item())
        assertThatThrownBy { (snapshot.items as MutableList<OrderItem>).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
        assertThat(snapshot.items.single().unitPrice).isEqualTo(money("12.50"))
    }

    @Test
    fun orderValidationRejectsInconsistentOptionalValuesAndEmptyItems() {
        val created = order()
        assertThatThrownBy {
            Order(created.id, OrderState.CONFIRMED, created.createdAt, null, null, created.items, 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            Order(created.id, created.state, created.createdAt, null, "reason", created.items, 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            Order(created.id, created.state, created.createdAt, null, null, emptyList(), 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
