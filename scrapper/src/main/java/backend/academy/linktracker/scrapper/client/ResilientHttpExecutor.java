package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.HttpResilienceProperties;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Единая точка применения Retry-политики к внешним HTTP-вызовам.
 */
@Component
public class ResilientHttpExecutor {
    /**
     * Имя retry-политики для метрик и отладки.
     * Нужен как стабильный идентификатор внешних HTTP-вызовов.
     */
    private static final String RETRY_NAME = "external-http-retry";
    /**
     * Источник конфигурации timeout/retry.
     * Нужен для централизованного управления устойчивостью из application-конфига.
     */
    private final HttpResilienceProperties properties;
    /**
     * Готовый экземпляр retry-политики.
     * Нужен для переиспользования одной и той же политики всеми внешними клиентами.
     */
    private final Retry retry;
    /**
     * Набор retryable HTTP-статусов.
     * Нужен для быстрых проверок принадлежности через contains.
     */
    private final Set<Integer> retryableStatuses;

    /**
     * Создает исполнитель HTTP-вызовов с политикой retry/fallback.
     *
     * @param properties конфигурация устойчивости внешних HTTP-вызовов
     */
    public ResilientHttpExecutor(HttpResilienceProperties properties) {
        this.properties = properties;
        this.retryableStatuses = Set.copyOf(properties.getRetry().getRetryableStatuses());
        this.retry = Retry.of(RETRY_NAME, buildRetryConfig());
    }

    /**
     * Выполняет внешнюю HTTP-операцию с retry-политикой.
     *
     * @param supplier HTTP-операция
     * @return результат HTTP-операции
     */
    public <T> T execute(Supplier<T> supplier) {
        return Retry.decorateSupplier(retry, supplier).get();
    }

    /**
     * Выполняет внешнюю HTTP-операцию с retry-политикой и fallback-значением
     * на случай любой RuntimeException.
     *
     * @param supplier HTTP-операция
     * @param fallbackSupplier поставщик fallback-значения
     * @return результат HTTP-операции либо fallback-значение при ошибке
     */
    public <T> T executeOrFallback(Supplier<T> supplier, Supplier<T> fallbackSupplier) {
        try {
            return execute(supplier);
        } catch (RuntimeException ex) {
            return fallbackSupplier.get();
        }
    }

    /**
     * Создает конфигурацию retry-политики из application-свойств.
     * Нужен для единообразного применения настроек ко всем HTTP-клиентам.
     *
     * @return готовая конфигурация retry
     */
    private RetryConfig buildRetryConfig() {
        HttpResilienceProperties.Retry retry = properties.getRetry();
        return RetryConfig.custom()
                .maxAttempts(retry.getMaxAttempts())
                .intervalFunction(buildIntervalFunction(retry))
                .retryOnException(this::isRetryableException)
                .failAfterMaxAttempts(true)
                .build();
    }

    /**
     * Выбирает функцию интервала между попытками согласно стратегии backoff.
     * Нужен для поддержки нескольких режимов ожидания (constant/exponential).
     *
     * @param retry настройки retry
     * @return функция вычисления паузы между попытками
     */
    private IntervalFunction buildIntervalFunction(HttpResilienceProperties.Retry retry) {
        Duration delay = retry.getDelay();
        if (retry.getBackoffStrategy() == HttpResilienceProperties.BackoffStrategy.EXPONENTIAL) {
            return IntervalFunction.ofExponentialBackoff(delay, retry.getExponentialMultiplier());
        }
        return IntervalFunction.of(delay);
    }

    /**
     * Определяет, нужно ли повторять ошибочный вызов.
     * Нужен для отделения retryable ошибок от неретрайбл-сценариев.
     *
     * @param throwable исключение, возникшее при вызове
     * @return {@code true}, если ошибку можно повторить
     */
    private boolean isRetryableException(Throwable throwable) {
        Optional<Integer> status = findStatusCode(throwable);
        return status.filter(retryableStatuses::contains).isPresent();
    }

    /**
     * Извлекает HTTP-статус из цепочки причин исключения.
     * Нужен для корректной обработки обернутых RestClient-исключений.
     *
     * @param throwable исключение верхнего уровня
     * @return HTTP-статус, если найден
     */
    private static Optional<Integer> findStatusCode(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RestClientResponseException exception) {
                return Optional.of(exception.getStatusCode().value());
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
