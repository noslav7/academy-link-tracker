package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Набор настроек подключения scrapper к bot-сервису.
 */
@ConfigurationProperties(prefix = "app.bot")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class BotClientProperties {

    /**
     * Базовый URL bot HTTP API.
     */
    @NotBlank
    @URL
    @Size(max = 2048)
    private String baseUrl;

    /**
     * Транспорт отправки уведомлений в bot.
     */
    @NotNull
    private TransportMode transport = TransportMode.KAFKA;

    /**
     * Хост gRPC-сервера bot.
     */
    @NotBlank
    @Size(max = 255)
    private String grpcHost = "localhost";

    /**
     * Порт gRPC-сервера bot.
     */
    @Min(1)
    @Max(65535)
    private int grpcPort = 9090;

    /**
     * Флаг включения fallback на Kafka при HTTP-транспорте.
     */
    private boolean httpFallbackToKafkaEnabled = true;

    /**
     * Параметры Circuit Breaker для HTTP-уведомлений в bot.
     */
    @Valid
    @NotNull
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /**
     * Настройки Circuit Breaker для деградации HTTP и fallback в Kafka.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class CircuitBreaker {

        /**
         * Размер sliding window для расчета процента ошибок.
         */
        @Positive
        private int slidingWindowSize = 10;

        /**
         * Минимум вызовов перед оценкой failure rate.
         */
        @Positive
        private int minimumRequiredCalls = 5;

        /**
         * Порог процента ошибок для перехода в OPEN.
         */
        @PositiveOrZero
        private float failureRateThreshold = 100.0f;

        /**
         * Количество пробных вызовов в HALF_OPEN.
         */
        @Positive
        private int permittedCallsInHalfOpenState = 5;

        /**
         * Ожидание в OPEN перед разрешением пробных вызовов.
         */
        @NotNull
        private Duration waitDurationInOpenState = Duration.ofSeconds(1);
    }
}
