package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки ограничения частоты входящих HTTP-запросов к публичным endpoint.
 *
 * <p>Нужны, чтобы управлять лимитированием через конфигурацию окружений
 * без изменения кода и пересборки сервиса.
 */
@ConfigurationProperties(prefix = "app.rate-limiting")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class RateLimitingProperties {

    /**
     * Флаг включения ограничения запросов.
     *
     * <p>Нужен для быстрого включения/отключения механизма на среде.
     */
    private boolean enabled = true;

    /**
     * Максимум разрешенных запросов за один период.
     *
     * <p>Нужен, чтобы задать пропускную способность для одного IP.
     */
    @Positive
    private int limitForPeriod = 100;

    /**
     * Длительность периода, за который учитывается лимит.
     *
     * <p>Нужна, чтобы определить временное окно накопления токенов.
     */
    @NotNull
    private Duration limitRefreshPeriod = Duration.ofMinutes(1);

    /**
     * Максимальное ожидание разрешения при исчерпании лимита.
     *
     * <p>Нужно, чтобы контролировать блокирующее ожидание и latency запроса.
     */
    @NotNull
    private Duration timeoutDuration = Duration.ZERO;

    /**
     * Публичные HTTP endpoint, на которые распространяется ограничение.
     *
     * <p>Нужны, чтобы ограничивать только внешние API-маршруты сервиса.
     */
    @NotEmpty
    private List<@NotBlank @Size(max = 256) String> publicPaths = List.of("/tg-chat/**", "/links", "/list", "/tags/**");
}
