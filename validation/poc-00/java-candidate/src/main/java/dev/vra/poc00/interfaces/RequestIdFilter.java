package dev.vra.poc00.interfaces;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Logger LOG = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString(); // Never trust an incoming correlation header.
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader("X-Request-Id", id);
        long started = System.nanoTime();
        try (var ignored = MDC.putCloseable("requestId", id)) {
            try {
                chain.doFilter(request, response);
            } finally {
                // No URL, body, headers, cookies, credentials, or exception payloads.
                LOG.info("event=http_request status={} durationMs={}",
                        response.getStatus(), (System.nanoTime() - started) / 1_000_000);
            }
        }
    }
}
