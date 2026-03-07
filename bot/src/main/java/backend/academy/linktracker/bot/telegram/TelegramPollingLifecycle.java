package backend.academy.linktracker.bot.telegram;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Управляет жизненным циклом Telegram long polling.
 */
@Order(2)
@Component
@RequiredArgsConstructor
public class TelegramPollingLifecycle implements ApplicationRunner {

    /**
     * Логгер класса.
     */
    private static final Logger log = LoggerFactory.getLogger(TelegramPollingLifecycle.class);

    /**
     * Клиент Telegram API.
     */
    private final TelegramBot telegramBot;
    /**
     * Параметры работы Telegram-клиента.
     */
    private final TelegramProperties telegramProperties;
    /**
     * Обработчик входящих обновлений Telegram.
     */
    private final TelegramUpdateProcessor updateProcessor;

    /**
     * Запускает слушатель обновлений, если polling включен.
     *
     * @param args аргументы запуска приложения
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!telegramProperties.isPollingEnabled()) {
            log.atInfo().addKeyValue("pollingEnabled", false).log("Telegram polling is disabled by configuration");
            return;
        }

        telegramBot.setUpdatesListener(updates -> {
            updates.forEach(updateProcessor::process);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        log.atInfo().addKeyValue("pollingEnabled", true).log("Telegram polling listener is started");
    }

    /**
     * Останавливает polling-слушатель при завершении приложения.
     */
    @PreDestroy
    public void stop() {
        telegramBot.removeGetUpdatesListener();
    }
}
