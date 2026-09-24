package dev.vra.inventory.adapter.in.web;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.vra.inventory.application.ReservationApplicationService;
import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationFailureException;
import dev.vra.inventory.application.ReservationResult;
import dev.vra.inventory.application.ReserveInventoryCommand;
import dev.vra.inventory.domain.StockStatus;
import dev.vra.platform.error.GlobalApiExceptionHandler;
import dev.vra.platform.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(InventoryReservationController.class)
class InventoryReservationControllerTest {

    private static final UUID SKU_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OWNER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID LOCATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationApplicationService reservationApplicationService;

    @Test
    void createsReservationAndGeneratesServerControlledRequestId(
            CapturedOutput output
    ) throws Exception {
        when(reservationApplicationService.reserve(any()))
                .thenAnswer(invocation -> {
                    ReserveInventoryCommand command =
                            invocation.getArgument(0);

                    return new ReservationResult(
                            command.reservationId(),
                            1
                    );
                });

        var result = mockMvc.perform(
                        post("/api/v1/inventory/reservations")
                                .header(
                                        RequestIdFilter.HEADER_NAME,
                                        "client-controlled-value"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                )
                .andExpect(status().isCreated())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.reservation_id").isString())
                .andExpect(jsonPath("$.inventory_version").value(1))
                .andExpect(jsonPath("$.request_id").isString())
                .andReturn();

        String requestId = result.getResponse()
                .getHeader(RequestIdFilter.HEADER_NAME);

        assertNotNull(requestId);
        assertNotEquals("client-controlled-value", requestId);
        UUID.fromString(requestId);

        String responseRequestId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.request_id"
        );

        assertEquals(requestId, responseRequestId);

        ArgumentCaptor<ReserveInventoryCommand> commandCaptor =
                ArgumentCaptor.forClass(ReserveInventoryCommand.class);

        verify(reservationApplicationService)
                .reserve(commandCaptor.capture());

        ReserveInventoryCommand command = commandCaptor.getValue();

        assertNotNull(command.reservationId());
        assertEquals(SKU_ID, command.inventoryKey().skuId());
        assertEquals(OWNER_ID, command.inventoryKey().ownerId());
        assertEquals(LOCATION_ID, command.inventoryKey().locationId());
        assertEquals(
                StockStatus.AVAILABLE,
                command.inventoryKey().stockStatus()
        );
        assertEquals(3, command.quantity());
        assertEquals(7, command.expectedVersion());

        String completionLog = findStructuredLog(
                output,
                "http_request_completed"
        );

        assertStructuredBaseFields(
                completionLog,
                RequestIdFilter.class.getName(),
                "INFO",
                requestId
        );
        assertEquals(
                201,
                ((Number) JsonPath.read(
                        completionLog,
                        "$.http_status"
                )).intValue()
        );
        assertEquals(
                "POST",
                JsonPath.read(completionLog, "$.http_method")
        );
        assertEquals(
                "/api/v1/inventory/reservations",
                JsonPath.read(completionLog, "$.http_path")
        );
    }

    @Test
    void rejectsInvalidRequestBeforeApplicationService() throws Exception {
        mockMvc.perform(
                        post("/api/v1/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "skuId": "%s",
                                          "ownerId": "%s",
                                          "locationId": "%s",
                                          "stockStatus": "AVAILABLE",
                                          "quantity": 0,
                                          "expectedVersion": 0
                                        }
                                        """.formatted(
                                        SKU_ID,
                                        OWNER_ID,
                                        LOCATION_ID
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.request_id").isString());
    }

    @Test
    void rejectsMalformedJsonWithStableError() throws Exception {
        mockMvc.perform(
                        post("/api/v1/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not-json")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.request_id").isString());
    }

    @Test
    void rejectsUnknownStockStatusWithStableError() throws Exception {
        mockMvc.perform(
                        post("/api/v1/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest()
                                        .replace(
                                                "\"AVAILABLE\"",
                                                "\"BROKEN\""
                                        ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.request_id").isString());
    }

    @Test
    void mapsInsufficientStockToStableConflictError() throws Exception {
        assertDomainFailure(
                ReservationFailureCode.INSUFFICIENT_STOCK,
                409,
                "INVENTORY_INSUFFICIENT_STOCK",
                "Insufficient stock"
        );
    }

    @Test
    void mapsUnknownInventoryToStableNotFoundError() throws Exception {
        assertDomainFailure(
                ReservationFailureCode.INVENTORY_NOT_FOUND,
                404,
                "INVENTORY_NOT_FOUND",
                "Inventory not found"
        );
    }

    @Test
    void mapsNonReservableInventoryToStableConflictError()
            throws Exception {
        assertDomainFailure(
                ReservationFailureCode.INVENTORY_NOT_RESERVABLE,
                409,
                "INVENTORY_NOT_RESERVABLE",
                "Inventory is not reservable"
        );
    }

    @Test
    void mapsVersionConflictToStableConflictError() throws Exception {
        assertDomainFailure(
                ReservationFailureCode.VERSION_CONFLICT,
                409,
                "INVENTORY_VERSION_CONFLICT",
                "Inventory version conflict"
        );
    }

    @Test
    void mapsUnexpectedFailureToSafeGenericInternalErrorAndStructuredLog(
            CapturedOutput output
    ) throws Exception {
        String sensitiveInternalMessage =
                "password=must-not-leak jdbc:postgresql://internal-host/vra";

        when(reservationApplicationService.reserve(any()))
                .thenThrow(
                        new IllegalStateException(
                                sensitiveInternalMessage
                        )
                );

        var result = mockMvc.perform(
                        post("/api/v1/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                )
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Internal server error")
                )
                .andExpect(jsonPath("$.request_id").isString())
                .andExpect(
                        content().string(
                                org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.containsString(
                                                sensitiveInternalMessage
                                        )
                                )
                        )
                )
                .andReturn();

        String requestId = result.getResponse()
                .getHeader(RequestIdFilter.HEADER_NAME);

        assertNotNull(requestId);
        UUID.fromString(requestId);

        assertEquals(
                requestId,
                JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.request_id"
                )
        );

        String failureLog = findStructuredLog(
                output,
                "http_request_failed"
        );

        assertStructuredBaseFields(
                failureLog,
                GlobalApiExceptionHandler.class.getName(),
                "ERROR",
                requestId
        );
        assertEquals(
                "INTERNAL_ERROR",
                JsonPath.read(failureLog, "$.error_code")
        );
        assertEquals(
                IllegalStateException.class.getName(),
                JsonPath.read(failureLog, "$.exception_type")
        );
        assertEquals(
                500,
                ((Number) JsonPath.read(
                        failureLog,
                        "$.http_status"
                )).intValue()
        );
        assertFalse(failureLog.contains(sensitiveInternalMessage));
    }

    private void assertDomainFailure(
            ReservationFailureCode failureCode,
            int expectedStatus,
            String expectedCode,
            String expectedMessage
    ) throws Exception {
        when(reservationApplicationService.reserve(any()))
                .thenThrow(
                        new ReservationFailureException(
                                failureCode,
                                "internal message must not leak"
                        )
                );

        mockMvc.perform(
                        post("/api/v1/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                )
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.request_id").isString())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME));
    }

    private static void assertStructuredBaseFields(
            String logLine,
            String expectedLogger,
            String expectedLevel,
            String expectedRequestId
    ) {
        String timestamp = JsonPath.read(logLine, "$['@timestamp']");
        String level = JsonPath.read(logLine, "$.log.level");
        String logger = JsonPath.read(logLine, "$.log.logger");
        String serviceName = JsonPath.read(
                logLine,
                "$.service.name"
        );
        String requestId = JsonPath.read(logLine, "$.request_id");

        assertNotNull(timestamp);
        assertFalse(timestamp.isBlank());
        assertEquals(expectedLevel, level);
        assertEquals(expectedLogger, logger);
        assertEquals("vra-backend", serviceName);
        assertEquals(expectedRequestId, requestId);
    }

    private static String findStructuredLog(
            CapturedOutput output,
            String message
    ) {
        String marker = "\"message\":\"" + message + "\"";

        return output.getAll()
                .lines()
                .filter(line -> line.startsWith("{"))
                .filter(line -> line.contains(marker))
                .reduce((first, second) -> second)
                .orElseThrow(
                        () -> new AssertionError(
                                "Structured log not found: "
                                        + message
                                        + System.lineSeparator()
                                        + output.getAll()
                        )
                );
    }

    private static String validRequest() {
        return """
                {
                  "skuId": "%s",
                  "ownerId": "%s",
                  "locationId": "%s",
                  "stockStatus": "AVAILABLE",
                  "quantity": 3,
                  "expectedVersion": 7
                }
                """.formatted(
                SKU_ID,
                OWNER_ID,
                LOCATION_ID
        );
    }
}
