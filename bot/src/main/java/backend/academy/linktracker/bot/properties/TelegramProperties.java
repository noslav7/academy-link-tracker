package backend.academy.linktracker.bot.properties;

import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Набор настроек Telegram-клиента из конфигурации приложения.
 */
@ConfigurationProperties(prefix = "app.telegram")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class TelegramProperties {

    /**
     * Базовый URL Telegram Bot API.
     */
    @NotEmpty
    @URL
    private String url;

    /**
     * Токен бота для аутентификации в Telegram API.
     */
    @NotEmpty
    private String token;

    /**
     * Интервал ожидания между запросами обновлений.
     */
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration updateListenerSleep = Duration.ofSeconds(1);

    /**
     * Флаг включения подробного режима Telegram-клиента.
     */
    private boolean debug;

    /**
     * Флаг включения long polling режима.
     */
    private boolean pollingEnabled = true;

    /**
     * Флаг автоматической регистрации команд в Telegram при старте.
     */
    private boolean setMyCommandsOnStartup = true;
}
