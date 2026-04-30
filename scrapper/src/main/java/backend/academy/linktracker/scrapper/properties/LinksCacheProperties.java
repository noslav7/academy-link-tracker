package backend.academy.linktracker.scrapper.properties;

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
     * TTL хранения ответа в кэше.
     */
    @NotNull
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration ttl = Duration.ofMinutes(5);
}
