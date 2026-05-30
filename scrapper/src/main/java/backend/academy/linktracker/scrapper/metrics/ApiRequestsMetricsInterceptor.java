package backend.academy.linktracker.scrapper.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor записи счетчика входящих API-запросов.
 */
@Component
public class ApiRequestsMetricsInterceptor implements HandlerInterceptor {
    private static final String ACTUATOR_PREFIX = "/actuator";
    private final ScrapperMetricsRecorder metricsRecorder;

    public ApiRequestsMetricsInterceptor(ScrapperMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestPath = request == null ? "" : request.getRequestURI();
        if (requestPath == null || requestPath.isBlank() || requestPath.startsWith(ACTUATOR_PREFIX)) {
            return true;
        }
        metricsRecorder.incrementApiRequest(resolveSource(requestPath));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String requestPath = request == null ? "" : request.getRequestURI();
        if (requestPath == null || requestPath.isBlank() || requestPath.startsWith(ACTUATOR_PREFIX)) {
            return;
        }
        boolean failedByStatus = response != null && response.getStatus() >= 400;
        if (failedByStatus || ex != null) {
            metricsRecorder.incrementApiRequestError(resolveSource(requestPath));
        }
    }

    private static String resolveSource(String requestPath) {
        String normalized = requestPath.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int separator = normalized.indexOf('/');
        String source = separator >= 0 ? normalized.substring(0, separator) : normalized;
        if (source.isBlank()) {
            return "unknown";
        }
        return source;
    }
}
