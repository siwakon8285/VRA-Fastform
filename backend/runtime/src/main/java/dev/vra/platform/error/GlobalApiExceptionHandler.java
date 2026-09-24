package dev.vra.platform.error;

import dev.vra.inventory.application.ReservationFailureCode;
import dev.vra.inventory.application.ReservationFailureException;
import dev.vra.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String INTERNAL_ERROR_MESSAGE =
            "Internal server error";

    @ExceptionHandler(ReservationFailureException.class)
    ResponseEntity<ApiErrorResponse> handleReservationFailure(
            ReservationFailureException error,
            HttpServletRequest request
    ) {
        ErrorMapping mapping = map(error.code());

        logRejectedRequest(mapping.code(), mapping.status());

        return ResponseEntity
                .status(mapping.status())
                .body(new ApiErrorResponse(
                        mapping.code(),
                        mapping.message(),
                        RequestIdFilter.requestId(request)
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException error,
            HttpServletRequest request
    ) {
        return invalidRequest(request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException error,
            HttpServletRequest request
    ) {
        return invalidRequest(request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpectedFailure(
            Exception error,
            HttpServletRequest request
    ) {
        String requestId = RequestIdFilter.requestId(request);

        LOGGER.atError()
                .addKeyValue("error_code", INTERNAL_ERROR_CODE)
                .addKeyValue(
                        "http_status",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                )
                .addKeyValue(
                        "exception_type",
                        error.getClass().getName()
                )
                .log("http_request_failed");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        INTERNAL_ERROR_CODE,
                        INTERNAL_ERROR_MESSAGE,
                        requestId
                ));
    }

    private ResponseEntity<ApiErrorResponse> invalidRequest(
            HttpServletRequest request
    ) {
        logRejectedRequest(
                "REQUEST_INVALID",
                HttpStatus.BAD_REQUEST
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "REQUEST_INVALID",
                        "Invalid request",
                        RequestIdFilter.requestId(request)
                ));
    }

    private void logRejectedRequest(
            String errorCode,
            HttpStatus status
    ) {
        LOGGER.atInfo()
                .addKeyValue("error_code", errorCode)
                .addKeyValue("http_status", status.value())
                .log("http_request_rejected");
    }

    private ErrorMapping map(ReservationFailureCode code) {
        return switch (code) {
            case INVALID_QUANTITY -> new ErrorMapping(
                    HttpStatus.BAD_REQUEST,
                    "INVENTORY_INVALID_QUANTITY",
                    "Reservation quantity must be greater than zero"
            );
            case INVENTORY_NOT_FOUND -> new ErrorMapping(
                    HttpStatus.NOT_FOUND,
                    "INVENTORY_NOT_FOUND",
                    "Inventory not found"
            );
            case INVENTORY_NOT_RESERVABLE -> new ErrorMapping(
                    HttpStatus.CONFLICT,
                    "INVENTORY_NOT_RESERVABLE",
                    "Inventory is not reservable"
            );
            case INSUFFICIENT_STOCK -> new ErrorMapping(
                    HttpStatus.CONFLICT,
                    "INVENTORY_INSUFFICIENT_STOCK",
                    "Insufficient stock"
            );
            case VERSION_CONFLICT -> new ErrorMapping(
                    HttpStatus.CONFLICT,
                    "INVENTORY_VERSION_CONFLICT",
                    "Inventory version conflict"
            );
        };
    }

    private record ErrorMapping(
            HttpStatus status,
            String code,
            String message
    ) {
    }
}
