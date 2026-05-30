package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.metrics.BotMetricsRecorder;
import backend.academy.linktracker.bot.service.TrackDialogService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Маршрутизирует входящие сообщения к обработчикам команд.
 */
@Component
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
     * Сервис пошагового диалога команды {@code /track}.
     */
    private final TrackDialogService trackDialogService;
    /**
     * Recorder пользовательских метрик bot-сервиса.
     */
    private final BotMetricsRecorder metricsRecorder;

    /**
     * Создает диспетчер и индексирует обработчики по имени команды.
     *
     * @param handlers список доступных обработчиков
     */
    public BotCommandDispatcher(List<BotCommandHandler> handlers, TrackDialogService trackDialogService) {
        this(handlers, trackDialogService, BotMetricsRecorder.noop());
    }

    @Autowired
    public BotCommandDispatcher(
            List<BotCommandHandler> handlers,
            TrackDialogService trackDialogService,
            BotMetricsRecorder metricsRecorder) {
        this.handlersByCommand = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        handler -> normalizeCommandToken(handler.command()).orElseThrow(), handler -> handler));
        this.supportedCommands = handlersByCommand.keySet();
        this.trackDialogService = trackDialogService;
        this.metricsRecorder = metricsRecorder;
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

        boolean commandMessage = isCommandMessage(messageText);
        Optional<String> normalizedCommand = normalizeCommandToken(messageText);

        Optional<String> activeDialogResponse =
                handleActiveTrackDialog(chatId, messageText, commandMessage, normalizedCommand);
        if (activeDialogResponse.isPresent()) {
            return activeDialogResponse.orElseThrow();
        }

        if (normalizedCommand.isEmpty()) {
            return BotMessages.UNKNOWN_COMMAND;
        }

        String command = normalizedCommand.orElseThrow();
        if (!supportedCommands.contains(command)) {
            return BotMessages.UNKNOWN_COMMAND;
        }

        BotCommandHandler handler = handlersByCommand.get(command);
        metricsRecorder.incrementCommandRequest(command);
        return handler.handle(chatId, messageText);
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

    /**
     * Проверяет, является ли сообщение командой Telegram.
     *
     * @param text текст входящего сообщения
     * @return {@code true}, если сообщение начинается с символа {@code '/'}
     */
    private static boolean isCommandMessage(String text) {
        String trimmed = text == null ? "" : text.trim();
        return !trimmed.isEmpty() && trimmed.charAt(0) == '/';
    }

    /**
     * Обрабатывает сообщение в контексте активного пошагового диалога {@code /track}.
     *
     * @param chatId идентификатор чата
     * @param messageText входной текст сообщения
     * @param commandMessage признак, что сообщение начинается с {@code '/'}
     * @param normalizedCommand нормализованный токен команды (если есть)
     * @return ответ диалога, если сообщение было обработано диалогом; иначе пустой результат
     */
    private Optional<String> handleActiveTrackDialog(
            long chatId, String messageText, boolean commandMessage, Optional<String> normalizedCommand) {
        if (!trackDialogService.isActive(chatId)) {
            return Optional.empty();
        }
        if (!commandMessage) {
            return Optional.of(trackDialogService.consume(chatId, messageText));
        }

        if (!normalizedCommand.map("/cancel"::equals).orElse(false)) {
            trackDialogService.cancel(chatId);
        }
        return Optional.empty();
    }
}
