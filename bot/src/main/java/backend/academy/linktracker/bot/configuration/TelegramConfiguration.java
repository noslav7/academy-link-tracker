package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация клиента Telegram Bot API.
 */
@Configuration
public class TelegramConfiguration {

    /**
     * Создает и настраивает экземпляр {@link TelegramBot}.
     *
     * @param properties параметры подключения к Telegram API
     * @return настроенный клиент Telegram
     */
    @Bean
    public TelegramBot telegramBot(TelegramProperties properties) {
        var builder = new TelegramBot.Builder(properties.getToken())
                .apiUrl(properties.getUrl())
                .updateListenerSleep(properties.getUpdateListenerSleep().toMillis());

        if (properties.isDebug()) {
            builder.debug();
        }

        return builder.build();
    }
}
