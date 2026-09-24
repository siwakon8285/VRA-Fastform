package dev.vra.poc00.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** Non-negative commerce value; not a signed finance/ledger model. */
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) throw new IllegalArgumentException("Amount must be non-negative.");
        // Numeric equality must not depend on input scale (1.0 equals 1.00).
        amount = amount.stripTrailingZeros();
    }
    public Money add(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) throw new DomainFailure.CurrencyMismatch();
        return new Money(amount.add(other.amount), currency);
    }
}
