package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatcher с Circuit Breaker для HTTP и fallback-транспортом Kafka.
 */
public class CircuitBreakerFallbackNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerFallbackNotificationDispatcher.class);

    private final NotificationDispatcher primaryDispatcher;
    private final NotificationDispatcher fallbackDispatcher;
    private final CircuitBreaker circuitBreaker;

    /**
     * Создает dispatcher, который отправляет через primary транспорт
     * и переключается на fallback при сбоях или OPEN-состоянии breaker-а.
     *
     * @param primaryDispatcher  основной транспорт (HTTP)
     * @param fallbackDispatcher альтернативный транспорт (Kafka)
     * @param circuitBreaker     breaker, ограничивающий деградацию HTTP
     */
    public CircuitBreakerFallbackNotificationDispatcher(
            NotificationDispatcher primaryDispatcher,
            NotificationDispatcher fallbackDispatcher,
            CircuitBreaker circuitBreaker) {
        this.primaryDispatcher = primaryDispatcher;
        this.fallbackDispatcher = fallbackDispatcher;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void dispatch(LinkUpdate update) {
        try {
            CircuitBreaker.decorateRunnable(circuitBreaker, () -> primaryDispatcher.dispatch(update))
                    .run();
        } catch (CallNotPermittedException exception) {
            dispatchViaFallback(update, exception, "Primary HTTP notification transport is OPEN, using Kafka fallback");
        } catch (RuntimeException exception) {
            dispatchViaFallback(update, exception, "Primary HTTP notification transport failed, using Kafka fallback");
        }
    }

    private void dispatchViaFallback(LinkUpdate update, RuntimeException exception, String message) {
        log.atWarn()
                .setCause(exception)
                .addKeyValue("state", circuitBreaker.getState())
                .addKeyValue("linkId", update.id())
                .log(message);
        fallbackDispatcher.dispatch(update);
    }
}
