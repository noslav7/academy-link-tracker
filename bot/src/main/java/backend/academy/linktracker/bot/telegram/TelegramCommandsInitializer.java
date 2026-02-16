package backend.academy.linktracker.bot.telegram;

import backend.academy.linktracker.bot.command.BotCommandDispatcher;
import backend.academy.linktracker.bot.command.BotCommandHandler;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class TelegramCommandsInitializer implements ApplicationRunner {

    private final TelegramBot telegramBot;
    private final TelegramProperties telegramProperties;
    private final BotCommandDispatcher commandDispatcher;

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

    private BotCommand toBotCommand(BotCommandHandler handler) {
        return new BotCommand(handler.command(), handler.description());
    }
}
