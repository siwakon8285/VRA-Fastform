package dev.vra.platform.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "request_id";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RequestIdFilter.class);

    private static final String ATTRIBUTE_NAME =
            RequestIdFilter.class.getName() + ".requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();

        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            LOGGER.atInfo()
                    .addKeyValue("http_method", request.getMethod())
                    .addKeyValue("http_path", request.getRequestURI())
                    .addKeyValue("http_status", response.getStatus())
                    .log("http_request_completed");

            MDC.remove(MDC_KEY);
        }
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE_NAME);

        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }

        throw new IllegalStateException(
                "Server request ID is unavailable for the current request"
        );
    }
}
