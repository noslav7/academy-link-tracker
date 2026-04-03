package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.command.BotCommandDispatcher;
import backend.academy.linktracker.bot.command.BotCommandHandler;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import backend.academy.linktracker.bot.telegram.TelegramUpdateProcessor;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Конфигурация клиента Telegram Bot API.
 */
@Configuration
public class TelegramConfiguration {
    private static final Logger log = LoggerFactory.getLogger(TelegramConfiguration.class);

    /**
     * Создает и настраивает экземпляр {@link TelegramBot}.
     *
     * @param properties параметры подключения к Telegram API
     * @param commandDispatcher источник зарегистрированных команд приложения
     * @param updateProcessor обработчик входящих обновлений Telegram
     * @return настроенный клиент Telegram
     */
    @Bean(destroyMethod = "removeGetUpdatesListener")
    public TelegramBot telegramBot(
            TelegramProperties properties,
            BotCommandDispatcher commandDispatcher,
            @Lazy TelegramUpdateProcessor updateProcessor) {
        var builder = new TelegramBot.Builder(properties.getToken())
                .apiUrl(properties.getUrl())
                .updateListenerSleep(properties.getUpdateListenerSleep().toMillis());

        if (properties.isDebug()) {
            builder.debug();
        }

        TelegramBot telegramBot = builder.build();
        registerCommandsOnStartup(telegramBot, properties, commandDispatcher);
        if (!properties.isPollingEnabled()) {
            log.atInfo().addKeyValue("pollingEnabled", false).log("Telegram polling is disabled by configuration");
            return telegramBot;
        }

        telegramBot.setUpdatesListener(updates -> {
            updates.forEach(updateProcessor::process);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        log.atInfo().addKeyValue("pollingEnabled", true).log("Telegram polling listener is started");
        return telegramBot;
    }

    /**
     * Регистрирует команды бота в Telegram при старте, если это включено конфигурацией.
     * При ошибке регистрации прерывает запуск приложения (fail-fast).
     *
     * @param telegramBot клиент Telegram API
     * @param properties параметры работы Telegram-клиента
     * @param commandDispatcher источник зарегистрированных команд приложения
     */
    private void registerCommandsOnStartup(
            TelegramBot telegramBot, TelegramProperties properties, BotCommandDispatcher commandDispatcher) {
        if (!properties.isSetMyCommandsOnStartup()) {
            log.atInfo()
                    .addKeyValue("setMyCommandsOnStartup", false)
                    .log("Telegram setMyCommands is disabled by configuration");
            return;
        }

        BotCommand[] commands =
                commandDispatcher.commands().stream().map(this::toBotCommand).toArray(BotCommand[]::new);

        try {
            BaseResponse response = telegramBot.execute(new SetMyCommands(commands));
            if (!response.isOk()) {
                log.atError()
                        .addKeyValue("commandsCount", commands.length)
                        .addKeyValue("telegramResponseOk", response.isOk())
                        .addKeyValue("telegramErrorCode", response.errorCode())
                        .addKeyValue("telegramDescription", response.description())
                        .log("Failed to register telegram commands");
                throw new IllegalStateException("Failed to register telegram commands: telegram api returned non-ok");
            }
            log.atInfo()
                    .addKeyValue("setMyCommandsOnStartup", true)
                    .addKeyValue("commandsCount", commands.length)
                    .log("Telegram commands are registered");
        } catch (RuntimeException ex) {
            log.atError()
                    .addKeyValue("commandsCount", commands.length)
                    .setCause(ex)
                    .log("Failed to register telegram commands");
            throw new IllegalStateException("Failed to register telegram commands", ex);
        }
    }

    /**
     * Преобразует обработчик команды приложения в Telegram-представление команды.
     *
     * @param handler обработчик команды приложения
     * @return описание команды для Telegram API
     */
    private BotCommand toBotCommand(BotCommandHandler handler) {
        return new BotCommand(handler.command(), handler.description());
    }
}
