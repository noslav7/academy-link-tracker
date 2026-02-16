package backend.academy.linktracker.bot.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BotCommandDispatcher {

    private final Map<String, BotCommandHandler> handlersByCommand;

    public BotCommandDispatcher(List<BotCommandHandler> handlers) {
        this.handlersByCommand = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(handler -> normalize(handler.command()), handler -> handler));
    }

    public String dispatch(String messageText, long chatId) {
        if (messageText == null || messageText.isBlank()) {
            return BotMessages.UNKNOWN_COMMAND;
        }

        String normalizedCommand = normalize(messageText);
        BotCommandHandler handler = handlersByCommand.get(normalizedCommand);
        if (handler == null) {
            return BotMessages.UNKNOWN_COMMAND;
        }

        return handler.handle(chatId);
    }

    public List<BotCommandHandler> commands() {
        return handlersByCommand.values().stream()
                .sorted((left, right) -> left.command().compareTo(right.command()))
                .toList();
    }

    private static String normalize(String rawCommand) {
        String firstToken = rawCommand.trim().split("\\s+", 2)[0];
        int mentionDelimiter = firstToken.indexOf('@');
        String withoutMention = mentionDelimiter >= 0 ? firstToken.substring(0, mentionDelimiter) : firstToken;
        return withoutMention.toLowerCase(Locale.ROOT);
    }
}
