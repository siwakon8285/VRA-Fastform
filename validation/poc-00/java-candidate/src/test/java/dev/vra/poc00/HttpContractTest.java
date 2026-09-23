package dev.vra.poc00;

import dev.vra.poc00.application.ReservationApplicationService;
import dev.vra.poc00.domain.*;
import dev.vra.poc00.interfaces.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

class HttpContractTest {
    private final SkuId sku = new SkuId(UUID.randomUUID());
    private ReservationApplicationService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(ReservationApplicationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReservationController(service))
                .setControllerAdvice(new ApiErrors()).addFilters(new RequestIdFilter()).build();
    }
    private String body(int quantity) {
        return "{\"skuId\":\"" + sku.value() + "\",\"quantity\":" + quantity + "}";
    }
    @Test void successUsesResponseDtoAndGeneratedRequestId() throws Exception {
        when(service.reserve(sku, 2)).thenReturn(new InventoryBalance(sku, 10, 2, 1));
        var result = mvc.perform(post("/poc/reservations").header("X-Request-Id", "untrusted")
                        .contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(8))
                .andExpect(jsonPath("$.quantity").value(2)).andExpect(jsonPath("$.reserved").value(2))
                .andExpect(jsonPath("$.onHand").value(10)).andReturn();
        assertThat(UUID.fromString(result.getResponse().getHeader("X-Request-Id"))).isNotNull();
    }
    @ParameterizedTest @ValueSource(ints = {0, -1})
    void invalidQuantity(int quantity) throws Exception {
        mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body(quantity)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.invalid"));
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"skuId\":\"bad\",\"quantity\":2}", "{broken"})
    void malformedRequestsAreSafe(String body) throws Exception {
        mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.invalid"))
                .andExpect(jsonPath("$.message").value("Invalid request."));
        verifyNoInteractions(service);
    }
    @Test void unknownSku() throws Exception {
        when(service.reserve(sku, 2)).thenThrow(new DomainFailure.UnknownSku());
        mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("inventory.unknown_sku"));
    }
    @Test void insufficientStock() throws Exception {
        when(service.reserve(sku, 2)).thenThrow(new DomainFailure.InsufficientStock());
        mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("inventory.insufficient_stock"));
    }
    @Test void internalErrorDoesNotLeakAndCorrelates() throws Exception {
        when(service.reserve(sku, 2)).thenThrow(new IllegalStateException("SELECT secret FROM /private/database password=example"));
        var result = mvc.perform(post("/poc/reservations").contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("internal.error"))
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andExpect(jsonPath("$.status").value(500)).andReturn();
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(result.getResponse().getContentAsString()).contains(id)
                .doesNotContain("SELECT", "secret", "password", "/private", "IllegalStateException", "stackTrace");
    }
}
