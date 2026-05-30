package backend.academy.linktracker.scrapper.metrics;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Аспект пересчета gauge links_on_track_total после мутаций ссылок.
 */
@Aspect
@Component
public class TrackedLinksGaugeRefreshAspect {
    private final TrackedLinksMetricsService trackedLinksMetricsService;

    public TrackedLinksGaugeRefreshAspect(TrackedLinksMetricsService trackedLinksMetricsService) {
        this.trackedLinksMetricsService = trackedLinksMetricsService;
    }

    @Around("execution(* backend.academy.linktracker.scrapper.service.LinkService.add(..))"
            + " || execution(* backend.academy.linktracker.scrapper.service.LinkService.remove(..))"
            + " || execution(* backend.academy.linktracker.scrapper.service.ChatService.remove(..))")
    public Object refreshAfterMutation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        trackedLinksMetricsService.refreshTrackedLinksGauge();
        return result;
    }
}
