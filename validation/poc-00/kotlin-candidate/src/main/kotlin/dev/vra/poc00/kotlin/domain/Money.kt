package dev.vra.poc00.kotlin.domain

import java.math.BigDecimal
import java.util.Currency

/**
 * Non-negative exact commerce price/value, not a signed Finance/ledger amount.
 * Equality deliberately compares decimal value instead of BigDecimal scale.
 */
class Money(amount: BigDecimal, val currency: Currency) {
    val amount: BigDecimal = amount.stripTrailingZeros()

    init {
        require(this.amount.signum() >= 0) { "Amount must be non-negative." }
    }

    fun add(other: Money): Money {
        if (currency != other.currency) throw DomainFailure.CurrencyMismatch()
        return Money(amount.add(other.amount), currency)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Money && currency == other.currency && amount.compareTo(other.amount) == 0)

    override fun hashCode(): Int = 31 * currency.hashCode() + amount.stripTrailingZeros().hashCode()

    override fun toString(): String = "Money(amount=$amount, currency=$currency)"
}
