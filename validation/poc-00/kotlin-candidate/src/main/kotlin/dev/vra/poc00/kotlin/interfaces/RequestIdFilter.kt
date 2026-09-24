package dev.vra.poc00.kotlin.interfaces

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val id = UUID.randomUUID().toString()
        request.setAttribute(ATTRIBUTE, id)
        response.setHeader("X-Request-Id", id)
        val started = System.nanoTime()

        MDC.putCloseable("requestId", id).use {
            try {
                chain.doFilter(request, response)
            } finally {
                // No URL, payload, headers, cookies, credentials or exception payloads.
                requestLogger.info(
                    "event=http_request status={} durationMs={}",
                    response.status,
                    (System.nanoTime() - started) / 1_000_000
                )
            }
        }
    }

    companion object {
        const val ATTRIBUTE = "dev.vra.poc00.kotlin.interfaces.RequestIdFilter.requestId"
        private val requestLogger = LoggerFactory.getLogger(RequestIdFilter::class.java)
    }
}
