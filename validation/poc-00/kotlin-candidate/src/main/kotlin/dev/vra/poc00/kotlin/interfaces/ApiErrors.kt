package dev.vra.poc00.kotlin.interfaces

import dev.vra.poc00.kotlin.domain.DomainFailure
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ApiErrors : ResponseEntityExceptionHandler() {
    data class Error(val code: String, val status: Int, val requestId: String?, val message: String)

    @ExceptionHandler(DomainFailure::class)
    fun domain(failure: DomainFailure, request: HttpServletRequest): ResponseEntity<Any> =
        when (failure) {
            is DomainFailure.InsufficientStock ->
                error(409, "inventory.insufficient_stock", "Insufficient stock.", request)
            is DomainFailure.UnknownSku ->
                error(404, "inventory.unknown_sku", "SKU was not found.", request)
            is DomainFailure.InvalidQuantity ->
                error(400, "request.invalid", "Quantity must be positive.", request)
            is DomainFailure.InvalidTransition ->
                error(409, "order.invalid_transition", "Order transition is not allowed.", request)
            is DomainFailure.InvalidIdempotencyKey ->
                error(400, "request.invalid_idempotency_key", "Invalid idempotency key.", request)
            is DomainFailure.CurrencyMismatch ->
                error(422, "money.currency_mismatch", "Currencies must match.", request)
            is DomainFailure.InvalidSkuId ->
                error(400, "request.invalid_sku_id", "Invalid SKU identifier.", request)
        }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val servletRequest = (request as ServletWebRequest).request
        val safe = error(
            statusCode.value(),
            if (statusCode.is4xxClientError) "request.invalid" else "internal.error",
            if (statusCode.is4xxClientError) "Invalid request." else "Internal server error.",
            servletRequest
        )
        return ResponseEntity.status(statusCode).headers(headers).body<Any>(safe.body)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        // Driver text and stack traces can disclose details; deliberately omit them.
        apiLogger.error("event=internal_error")
        return error(500, "internal.error", "Internal server error.", request)
    }

    private fun error(status: Int, code: String, message: String, request: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity.status(status).body<Any>(
            Error(code, status, request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String, message)
        )

    companion object {
        private val apiLogger = LoggerFactory.getLogger(ApiErrors::class.java)
    }
}
