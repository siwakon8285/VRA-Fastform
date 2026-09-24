package dev.vra.poc00.interfaces;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Safe fallback for servlet error dispatches outside normal MVC exception handling. */
@RestController
public class SafeErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ApiErrors.Error> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        return ResponseEntity.status(status).body(new ApiErrors.Error(
                status < 500 ? "request.invalid" : "internal.error", status,
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE),
                status < 500 ? "Invalid request." : "Internal server error."));
    }
}
