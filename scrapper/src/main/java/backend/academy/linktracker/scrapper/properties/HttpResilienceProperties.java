package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурация устойчивости для внешних HTTP-вызовов.
 * Нужна для управления timeout/retry-политикой без изменения кода клиентов.
 */
@ConfigurationProperties(prefix = "app.http-resilience")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class HttpResilienceProperties {

    /**
     * Максимальное время ожидания ответа внешнего HTTP-сервиса.
     * Нужен для ограничения времени блокировки потока при проблемах сети.
     */
    @NotNull
    private Duration timeout = Duration.ofSeconds(2);

    /**
     * Параметры Retry для внешних HTTP-вызовов.
     * Нужны для тонкой настройки поведения повторных попыток.
     */
    @Valid
    @NotNull
    private Retry retry = new Retry();

    /**
     * Конфигурация механизма повторных попыток.
     * Нужна как отдельный блок, чтобы группировать связанные retry-параметры.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Retry {

        /**
         * Максимальное число попыток выполнения (включая первую).
         * Нужно для ограничения числа повторов при недоступности внешнего API.
         */
        @Min(1)
        private int maxAttempts = 3;

        /**
         * Базовый интервал между повторами.
         * Нужен для контроля нагрузки на внешний сервис между попытками.
         */
        @NotNull
        private Duration delay = Duration.ofMillis(200);

        /**
         * Стратегия формирования интервала между попытками.
         * Нужна для выбора режима ожидания (постоянный/экспоненциальный).
         */
        @NotNull
        private BackoffStrategy backoffStrategy = BackoffStrategy.CONSTANT;

        /**
         * Множитель для экспоненциальной стратегии backoff.
         * Нужен для роста паузы между попытками при повторяющихся ошибках.
         */
        @DecimalMin("1.0")
        private double exponentialMultiplier = 2.0d;

        /**
         * HTTP-статусы, при которых выполняется повтор запроса.
         * Нужны для явного управления тем, какие ответы считаются временными сбоями.
         */
        @NotEmpty
        private List<Integer> retryableStatuses = List.of(500, 502, 503, 504);
    }

    /**
     * Поддерживаемые стратегии вычисления интервала между retry-попытками.
     * Нужны как фиксированный набор допустимых вариантов backoff.
     */
    public enum BackoffStrategy {
        CONSTANT,
        EXPONENTIAL
    }
}
