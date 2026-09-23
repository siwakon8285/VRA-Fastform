package dev.vra.poc00.interfaces;

import dev.vra.poc00.domain.DomainFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    public record Error(String code, int status, String requestId, String message) {}

    @ExceptionHandler(DomainFailure.class)
    ResponseEntity<Object> domain(DomainFailure failure, HttpServletRequest request) {
        return switch (failure) {
            case DomainFailure.InsufficientStock ignored -> error(409, "inventory.insufficient_stock", "Insufficient stock.", request);
            case DomainFailure.UnknownSku ignored -> error(404, "inventory.unknown_sku", "SKU was not found.", request);
            case DomainFailure.InvalidQuantity ignored -> error(400, "request.invalid", "Quantity must be positive.", request);
            case DomainFailure.InvalidTransition ignored -> error(409, "order.invalid_transition", "Order transition is not allowed.", request);
            case DomainFailure.InvalidIdempotencyKey ignored -> error(400, "request.invalid_idempotency_key", "Invalid idempotency key.", request);
            case DomainFailure.CurrencyMismatch ignored -> error(422, "money.currency_mismatch", "Currencies must match.", request);
        };
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var servletRequest = ((ServletWebRequest) request).getRequest();
        var safe = error(status.value(), status.is4xxClientError() ? "request.invalid" : "internal.error",
                status.is4xxClientError() ? "Invalid request." : "Internal server error.", servletRequest);
        return new ResponseEntity<>(safe.getBody(), headers, status);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, HttpServletRequest request) {
        // Deliberately omit exception message/stack: driver messages may contain sensitive data.
        LoggerFactory.getLogger(ApiErrors.class).error("event=internal_error");
        return error(500, "internal.error", "Internal server error.", request);
    }

    private static ResponseEntity<Object> error(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(code, status,
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE), message));
    }
}
