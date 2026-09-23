package dev.vra.poc00.kotlin.domain

import java.util.UUID

@JvmInline
value class OrderId(val value: UUID)

@JvmInline
value class SkuId(val value: UUID)

@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        if (value.isBlank() || value.length > 128) throw DomainFailure.InvalidIdempotencyKey()
    }
}
