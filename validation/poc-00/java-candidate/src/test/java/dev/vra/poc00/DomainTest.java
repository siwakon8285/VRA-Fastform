package dev.vra.poc00;

import dev.vra.poc00.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DomainTest {
    private static final SkuId SKU = new SkuId(UUID.randomUUID());
    private static final Currency THB = Currency.getInstance("THB");
    private static Money money(String amount) { return new Money(new BigDecimal(amount), THB); }
    private static OrderItem item() { return new OrderItem(SKU, "Synthetic tea", 2, money("12.50")); }
    private static Order order() {
        return Order.create(new OrderId(UUID.randomUUID()), Instant.parse("2026-01-01T00:00:00Z"), List.of(item()));
    }

    @Test void validMoneyHasExactValueAndScaleIndependentEquality() {
        assertThat(money("12.50").amount()).isEqualByComparingTo("12.50");
        assertThat(money("12.50")).isEqualTo(money("12.500"));
    }
    @Test void negativeMoneyRejected() {
        assertThatThrownBy(() -> money("-0.01")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void moneyRequiresAmountAndCurrency() {
        assertThatNullPointerException().isThrownBy(() -> new Money(null, THB));
        assertThatNullPointerException().isThrownBy(() -> new Money(BigDecimal.ONE, null));
    }
    @Test void sameCurrencyArithmeticIsExact() {
        assertThat(money("0.1").add(money("0.2"))).isEqualTo(money("0.3"));
    }
    @Test void currencyMismatchRejected() {
        assertThatThrownBy(() -> money("1").add(new Money(BigDecimal.ONE, Currency.getInstance("USD"))))
                .isInstanceOf(DomainFailure.CurrencyMismatch.class);
    }
    @ParameterizedTest @ValueSource(ints = {0, -1})
    void invalidQuantityRejected(int quantity) {
        assertThatThrownBy(() -> new InventoryBalance(SKU, 5, 0, 0).reserve(quantity))
                .isInstanceOf(DomainFailure.InvalidQuantity.class);
        assertThatThrownBy(() -> new InventoryReservation(UUID.randomUUID(), SKU, quantity, InventoryReservation.State.ACTIVE))
                .isInstanceOf(DomainFailure.InvalidQuantity.class);
        assertThatThrownBy(() -> new OrderItem(SKU, "Tea", quantity, money("1")))
                .isInstanceOf(DomainFailure.InvalidQuantity.class);
    }
    @Test void validBalanceAndAvailableCalculation() {
        var original = new InventoryBalance(SKU, 10, 3, 0);
        assertThat(original.available()).isEqualTo(7);
        var reserved = original.reserve(7);
        assertThat(reserved.available()).isZero();
        assertThat(original.reserved()).isEqualTo(3);
        assertThatThrownBy(() -> reserved.reserve(1)).isInstanceOf(DomainFailure.InsufficientStock.class);
    }
    @Test void invalidBalancesRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new InventoryBalance(SKU, 1, 2, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new InventoryBalance(SKU, -1, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new InventoryBalance(SKU, 1, -1, 0));
    }
    @Test void validOrderTransitionsAndOptionalConfirmation() {
        var created = order();
        assertThat(created.confirmedAt()).isEmpty();
        assertThat(created.cancellationReason()).isEmpty();
        var pending = created.pendingPayment();
        var time = created.createdAt().plusSeconds(1);
        var confirmed = pending.confirm(time);
        assertThat(confirmed.state()).isEqualTo(OrderState.CONFIRMED);
        assertThat(confirmed.confirmedAt()).contains(time);
        assertThat(confirmed.cancellationReason()).isEmpty();
        assertThat(created.state()).isEqualTo(OrderState.CREATED);
    }
    @Test void invalidOrderTransitionRejected() {
        assertThatThrownBy(() -> order().confirm(Instant.now())).isInstanceOf(DomainFailure.InvalidTransition.class);
    }
    @Test void cancelledCannotBeConfirmedAndHasOptionalReason() {
        var cancelled = order().pendingPayment().cancel("Buyer requested cancellation");
        assertThat(cancelled.confirmedAt()).isEmpty();
        assertThat(cancelled.cancellationReason()).contains("Buyer requested cancellation");
        assertThatThrownBy(() -> cancelled.confirm(Instant.now())).isInstanceOf(DomainFailure.InvalidTransition.class);
        // V1 legacy cancelled orders legitimately have no cancellation reason.
        var legacy = new Order(cancelled.id(), cancelled.state(), cancelled.createdAt(),
                Optional.empty(), Optional.empty(), cancelled.items(), cancelled.version());
        assertThat(legacy.cancellationReason()).isEmpty();
    }
    @Test void everyStateTransitionMatchesFrozenGraph() {
        for (var from : OrderState.values()) for (var to : OrderState.values()) {
            boolean valid = from == OrderState.CREATED && to == OrderState.PENDING_PAYMENT
                    || from == OrderState.PENDING_PAYMENT && (to == OrderState.CONFIRMED || to == OrderState.CANCELLED);
            if (valid) assertThatCode(() -> from.requireTransitionTo(to)).doesNotThrowAnyException();
            else assertThatThrownBy(() -> from.requireTransitionTo(to)).isInstanceOf(DomainFailure.InvalidTransition.class);
        }
    }
    @Test void invalidIdempotencyKeysRejected() {
        for (String value : new String[] {null, "", "  ", "x".repeat(129)})
            assertThatThrownBy(() -> new IdempotencyKey(value)).isInstanceOf(DomainFailure.InvalidIdempotencyKey.class);
        assertThat(new IdempotencyKey("request-1").value()).isEqualTo("request-1");
    }
    @Test void purchaseSnapshotsCannotBeMutatedThroughCallerCollections() {
        var mutable = new ArrayList<>(List.of(item()));
        var order = Order.create(new OrderId(UUID.randomUUID()), Instant.now(), mutable);
        mutable.set(0, new OrderItem(SKU, "Changed name", 1, money("99")));
        assertThat(order.items().getFirst()).isEqualTo(item());
        assertThatThrownBy(() -> order.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(order.items().getFirst().unitPrice()).isEqualTo(money("12.50"));
    }
}
