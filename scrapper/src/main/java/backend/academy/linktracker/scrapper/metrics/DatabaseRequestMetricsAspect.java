package backend.academy.linktracker.scrapper.metrics;

import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Аспект записи метрик длительности DB-операций.
 */
@Aspect
@Component
public class DatabaseRequestMetricsAspect {
    private static final String DATABASE_SCOPE = "database";

    private final ScrapperMetricsRecorder metricsRecorder;

    public DatabaseRequestMetricsAspect(ScrapperMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
    }

    @Around("execution(* backend.academy.linktracker.scrapper.repository..*(..))"
            + " || execution(* backend.academy.linktracker.scrapper.service.outbox.NotificationOutboxRepository.*(..))")
    public Object recordDatabaseDuration(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            metricsRecorder.recordDuration(DATABASE_SCOPE, resolveScopeType(joinPoint), System.nanoTime() - startedAt);
        }
    }

    private static String resolveScopeType(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getMethod().getName();
        return className.toLowerCase(Locale.ROOT) + "." + methodName;
    }
}
