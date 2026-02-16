package backend.academy.linktracker.bot.telegram;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class TelegramPollingLifecycle implements ApplicationRunner {

    private final TelegramBot telegramBot;
    private final TelegramProperties telegramProperties;
    private final TelegramUpdateProcessor updateProcessor;

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

    @PreDestroy
    public void stop() {
        telegramBot.removeGetUpdatesListener();
    }
}
