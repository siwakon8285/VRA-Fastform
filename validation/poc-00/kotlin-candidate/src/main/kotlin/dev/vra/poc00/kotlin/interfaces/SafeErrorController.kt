package dev.vra.poc00.kotlin.interfaces

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SafeErrorController : ErrorController {
    @RequestMapping("/error")
    fun error(request: HttpServletRequest): ResponseEntity<ApiErrors.Error> {
        val status = (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int)
            ?.takeIf { it in 400..599 } ?: 500
        return ResponseEntity.status(status).body(
            ApiErrors.Error(
                if (status < 500) "request.invalid" else "internal.error",
                status,
                request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String,
                if (status < 500) "Invalid request." else "Internal server error."
            )
        )
    }
}
