package dev.vra.poc00.kotlin

import dev.vra.poc00.kotlin.application.ReservationApplicationService
import dev.vra.poc00.kotlin.domain.DomainFailure
import dev.vra.poc00.kotlin.domain.InventoryBalance
import dev.vra.poc00.kotlin.domain.SkuId
import dev.vra.poc00.kotlin.interfaces.ApiErrors
import dev.vra.poc00.kotlin.interfaces.RequestIdFilter
import dev.vra.poc00.kotlin.interfaces.ReservationController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class HttpContractTest {
    private val sku = SkuId(UUID.randomUUID())
    private lateinit var service: ReservationApplicationService
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        service = Mockito.mock(ReservationApplicationService::class.java)
        val builder: StandaloneMockMvcBuilder = MockMvcBuilders.standaloneSetup(ReservationController(service))
        builder.setControllerAdvice(*arrayOf<Any>(ApiErrors()))
        builder.addFilters<StandaloneMockMvcBuilder>(RequestIdFilter())
        mvc = builder.build()
    }

    private fun body(quantity: Int) =
        """{"skuId":"${sku.value}","quantity":$quantity}"""

    @Test
    fun successfulRequestReturnsDtoAndGeneratedRequestId() {
        Mockito.`when`(service.reserve(sku, 2)).thenReturn(InventoryBalance(sku, 10, 2, 1))
        val result = mvc.perform(
            post("/poc/reservations").header("X-Request-Id", "untrusted")
                .contentType(MediaType.APPLICATION_JSON).content(body(2))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(8))
            .andExpect(jsonPath("$.quantity").value(2))
            .andExpect(jsonPath("$.reserved").value(2))
            .andExpect(jsonPath("$.onHand").value(10))
            .andReturn()
        assertThat(UUID.fromString(result.response.getHeader("X-Request-Id"))).isNotNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun nonPositiveQuantityIsSafe400(quantity: Int) {
        mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body(quantity)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("request.invalid"))
        verifyNoInteractions(service)
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", """{"skuId":"bad","quantity":2}""", "{broken"])
    fun malformedRequestIsSafe400(body: String) {
        mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("request.invalid"))
            .andExpect(jsonPath("$.message").value("Invalid request."))
        verifyNoInteractions(service)
    }

    @Test
    fun unknownSkuIsMapped() {
        Mockito.`when`(service.reserve(sku, 2)).thenThrow(DomainFailure.UnknownSku())
        mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body(2)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("inventory.unknown_sku"))
    }

    @Test
    fun insufficientStockIsStable409() {
        Mockito.`when`(service.reserve(sku, 2)).thenThrow(DomainFailure.InsufficientStock())
        mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body(2)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("inventory.insufficient_stock"))
    }

    @Test
    fun internalFailureDoesNotLeakDetailsAndCorrelatesRequest() {
        Mockito.`when`(service.reserve(sku, 2)).thenThrow(
            IllegalStateException("SELECT secret FROM /private/database password=example")
        )
        val result = mvc.perform(
            post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body(2))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("internal.error"))
            .andExpect(jsonPath("$.message").value("Internal server error."))
            .andExpect(jsonPath("$.status").value(500))
            .andReturn()
        val id = result.response.getHeader("X-Request-Id")
        assertThat(result.response.contentAsString).contains(id)
            .doesNotContain("SELECT", "secret", "password", "/private", "IllegalStateException", "stackTrace")
    }
}
