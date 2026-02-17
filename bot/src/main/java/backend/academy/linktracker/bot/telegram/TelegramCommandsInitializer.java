package backend.academy.linktracker.bot.telegram;

import backend.academy.linktracker.bot.command.BotCommandDispatcher;
import backend.academy.linktracker.bot.command.BotCommandHandler;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1)
@Component
@RequiredArgsConstructor
/**
 * Регистрирует команды бота в Telegram при запуске приложения.
 */
public class TelegramCommandsInitializer implements ApplicationRunner {

    /**
     * Логгер класса.
     */
    private static final Logger log = LoggerFactory.getLogger(TelegramCommandsInitializer.class);

    /**
     * Клиент Telegram API.
     */
    private final TelegramBot telegramBot;
    /**
     * Параметры работы Telegram-клиента.
     */
    private final TelegramProperties telegramProperties;
    /**
     * Источник зарегистрированных команд приложения.
     */
    private final BotCommandDispatcher commandDispatcher;

    /**
     * Выполняет регистрацию команд в Telegram, если это включено конфигурацией.
     *
     * @param args аргументы запуска приложения
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!telegramProperties.isSetMyCommandsOnStartup()) {
            log.atInfo()
                    .addKeyValue("setMyCommandsOnStartup", false)
                    .log("Telegram setMyCommands is disabled by configuration");
            return;
        }

        BotCommand[] commands =
                commandDispatcher.commands().stream().map(this::toBotCommand).toArray(BotCommand[]::new);

        try {
            telegramBot.execute(new SetMyCommands(commands));
            log.atInfo()
                    .addKeyValue("setMyCommandsOnStartup", true)
                    .addKeyValue("commandsCount", commands.length)
                    .log("Telegram commands are registered");
        } catch (RuntimeException ex) {
            log.atWarn()
                    .addKeyValue("commandsCount", commands.length)
                    .setCause(ex)
                    .log("Failed to register telegram commands");
        }
    }

    /**
     * Преобразует обработчик команды в формат Telegram API.
     *
     * @param handler обработчик команды
     * @return описание команды для Telegram
     */
    private BotCommand toBotCommand(BotCommandHandler handler) {
        return new BotCommand(handler.command(), handler.description());
    }
}
