package backend.academy.linktracker.bot.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
/**
 * Маршрутизирует входящие сообщения к обработчикам команд.
 */
public class BotCommandDispatcher {

    /**
     * Максимальная длина текста сообщения по правилам Telegram Bot API:
     * sendMessage.text -> 1-4096 символов.
     * Источник: https://core.telegram.org/bots/api#sendmessage
     */
    private static final int MAX_MESSAGE_TEXT_LENGTH = 4096;
    /**
     * Максимальная длина имени команды в Telegram:
     * BotCommand.command -> 1-32 символа (без ведущего '/').
     * Источник: https://core.telegram.org/bots/api#botcommand
     */
    private static final int MAX_BOT_COMMAND_LENGTH = 32;
    /**
     * Максимальная длина username в Telegram:
     * username -> до 32 символов.
     * Источник: https://core.telegram.org/bots/api#user
     */
    private static final int MAX_BOT_USERNAME_LENGTH = 32;
    /**
     * Максимальная длина токена команды вида /command@bot_username:
     * '/' + command(32) + '@' + username(32) = 66.
     */
    private static final int MAX_COMMAND_TOKEN_LENGTH = 1 + MAX_BOT_COMMAND_LENGTH + 1 + MAX_BOT_USERNAME_LENGTH;

    /**
     * Сопоставление нормализованной команды и ее обработчика.
     */
    private final Map<String, BotCommandHandler> handlersByCommand;
    /**
     * Набор команд, поддерживаемых приложением.
     */
    private final Set<String> supportedCommands;

    /**
     * Создает диспетчер и индексирует обработчики по имени команды.
     *
     * @param handlers список доступных обработчиков
     */
    public BotCommandDispatcher(List<BotCommandHandler> handlers) {
        this.handlersByCommand = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        handler -> normalizeCommandToken(handler.command()).orElseThrow(), handler -> handler));
        this.supportedCommands = handlersByCommand.keySet();
    }

    /**
     * Обрабатывает входной текст и возвращает ответ соответствующей команды.
     *
     * @param messageText входной текст сообщения
     * @param chatId идентификатор чата
     * @return текст ответа пользователю
     */
    public String dispatch(String messageText, long chatId) {
        if (!isValidMessageText(messageText)) {
            return BotMessages.UNKNOWN_COMMAND;
        }

        Optional<String> normalizedCommand = normalizeCommandToken(messageText);
        if (normalizedCommand.isEmpty() || !supportedCommands.contains(normalizedCommand.get())) {
            return BotMessages.UNKNOWN_COMMAND;
        }

        BotCommandHandler handler = handlersByCommand.get(normalizedCommand.get());
        return handler.handle(chatId);
    }

    /**
     * Возвращает отсортированный список доступных обработчиков команд.
     *
     * @return список обработчиков по имени команды
     */
    public List<BotCommandHandler> commands() {
        return handlersByCommand.values().stream()
                .sorted((left, right) -> left.command().compareTo(right.command()))
                .toList();
    }

    /**
     * Проверяет корректность входного текста по базовым ограничениям.
     *
     * @param messageText входной текст сообщения
     * @return {@code true}, если текст можно обрабатывать
     */
    private static boolean isValidMessageText(String messageText) {
        return messageText != null && !messageText.isBlank() && messageText.length() <= MAX_MESSAGE_TEXT_LENGTH;
    }

    /**
     * Извлекает и нормализует токен команды из произвольного текста сообщения.
     *
     * @param rawCommand исходный текст сообщения
     * @return нормализованный токен команды в нижнем регистре или пустое значение
     */
    private static Optional<String> normalizeCommandToken(String rawCommand) {
        String firstToken = rawCommand.trim().split("\\s+", 2)[0];
        if (firstToken.isBlank() || firstToken.length() > MAX_COMMAND_TOKEN_LENGTH) {
            return Optional.empty();
        }

        int mentionDelimiter = firstToken.indexOf('@');
        String withoutMention = mentionDelimiter >= 0 ? firstToken.substring(0, mentionDelimiter) : firstToken;
        if (withoutMention.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(withoutMention.toLowerCase(Locale.ROOT));
    }
}
