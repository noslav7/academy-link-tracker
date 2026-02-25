package backend.academy.linktracker.bot.command;

import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
/**
 * Обработчик команды {@code /help}.
 */
public class HelpCommandHandler extends AbstractBotCommandHandler {

    /**
     * Источник зарегистрированных команд приложения.
     */
    private final Supplier<BotCommandDispatcher> commandDispatcherSupplier;
    /**
     * Сервис форматирования текста справки.
     */
    private final CommandHelpMessageFormatter helpMessageFormatter;

    /**
     * Создает обработчик команды {@code /help}.
     *
     * @param commandDispatcher источник зарегистрированных команд приложения
     * @param helpMessageFormatter сервис форматирования текста справки
     */
    @Autowired
    public HelpCommandHandler(
            ObjectProvider<BotCommandDispatcher> commandDispatcherProvider,
            CommandHelpMessageFormatter helpMessageFormatter) {
        this(commandDispatcherProvider::getObject, helpMessageFormatter);
    }

    /**
     * Создает обработчик команды {@code /help} с явным источником команд.
     *
     * @param commandDispatcherSupplier поставщик диспетчера команд
     * @param helpMessageFormatter сервис форматирования текста справки
     */
    HelpCommandHandler(
            Supplier<BotCommandDispatcher> commandDispatcherSupplier,
            CommandHelpMessageFormatter helpMessageFormatter) {
        super("/help", "список доступных команд");
        this.commandDispatcherSupplier = commandDispatcherSupplier;
        this.helpMessageFormatter = helpMessageFormatter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String handle(long chatId, String messageText) {
        return helpMessageFormatter.format(commandDispatcherSupplier.get().commands());
    }
}
