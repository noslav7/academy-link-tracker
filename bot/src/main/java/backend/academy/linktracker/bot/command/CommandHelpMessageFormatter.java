package backend.academy.linktracker.bot.command;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
/**
 * Формирует текст справки со списком доступных команд.
 */
public class CommandHelpMessageFormatter {

    /**
     * Собирает help-сообщение из списка обработчиков команд.
     *
     * @param handlers обработчики, которые должны попасть в справку
     * @return готовый текст ответа для команды {@code /help}
     */
    public String format(List<BotCommandHandler> handlers) {
        StringBuilder messageBuilder = new StringBuilder("Доступные команды:\n");
        for (BotCommandHandler handler : handlers) {
            messageBuilder
                    .append(handler.command())
                    .append(" - ")
                    .append(handler.description())
                    .append('\n');
        }
        return messageBuilder.toString();
    }
}
