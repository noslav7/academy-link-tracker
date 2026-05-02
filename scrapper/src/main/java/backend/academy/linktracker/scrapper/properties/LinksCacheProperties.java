package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки кэширования ответа GET /list.
 */
@ConfigurationProperties(prefix = "app.cache.links")
@Component
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class LinksCacheProperties {

    /**
     * Включает/отключает использование Valkey-кэша для списка ссылок.
     */
    private boolean enabled = true;

    /**
     * Режим кэширования.
     */
    @NotNull
    private CacheMode mode = CacheMode.CLIENT_SIDE;

    /**
     * TTL хранения ответа в кэше.
     */
    @NotNull
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration ttl = Duration.ofMinutes(5);

    /**
     * Проверяет, что TTL кэша задан положительным значением.
     *
     * @return {@code true}, если TTL больше нуля
     * @implNote Нужно, чтобы приложение не запускалось с некорректной конфигурацией времени жизни кэша.
     */
    // Метод вызывается Bean Validation через рефлексию, поэтому PMD не видит прямого использования.
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @AssertTrue(message = "app.cache.links.ttl must be positive")
    private boolean isTtlPositive() {
        return ttl != null && !ttl.isNegative() && !ttl.isZero();
    }

    public enum CacheMode {
        NONE,
        VALKEY,
        CLIENT_SIDE
    }
}
