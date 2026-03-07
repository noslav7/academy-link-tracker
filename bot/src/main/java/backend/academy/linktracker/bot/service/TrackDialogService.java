package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.command.BotMessages;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Управляет пошаговым диалогом команды {@code /track} для каждого чата.
 */
@Service
public class TrackDialogService {

    /**
     * Активные диалоги по идентификатору чата.
     */
    private final Map<Long, TrackState> activeDialogs = new ConcurrentHashMap<>();
    /**
     * Компонент валидации и разбора текстового ввода пользователя.
     */
    private final TrackDialogInputParser inputParser;
    /**
     * Компонент отправки подготовленных данных в scrapper.
     */
    private final TrackDialogSubmissionService submissionService;

    /**
     * Создает сервис управления диалогом команды {@code /track}.
     *
     * @param inputParser компонент валидации пользовательского ввода
     * @param submissionService компонент отправки данных в scrapper
     */
    public TrackDialogService(TrackDialogInputParser inputParser, TrackDialogSubmissionService submissionService) {
        this.inputParser = inputParser;
        this.submissionService = submissionService;
    }

    /**
     * Начинает новый диалог отслеживания для чата.
     *
     * @param chatId идентификатор чата
     * @return текст следующего шага диалога
     */
    public String start(long chatId) {
        activeDialogs.put(chatId, TrackState.initial());
        return BotMessages.TRACK_ENTER_LINK;
    }

    /**
     * Проверяет, запущен ли диалог {@code /track} в указанном чате.
     *
     * @param chatId идентификатор чата
     * @return {@code true}, если диалог активен
     */
    public boolean isActive(long chatId) {
        return activeDialogs.containsKey(chatId);
    }

    /**
     * Прерывает активный диалог в чате.
     *
     * @param chatId идентификатор чата
     * @return сообщение о результате отмены
     */
    public String cancel(long chatId) {
        return activeDialogs.remove(chatId) == null ? BotMessages.CANCEL_NO_ACTIVE : BotMessages.TRACK_CANCELLED;
    }

    /**
     * Обрабатывает очередное сообщение пользователя в рамках диалога.
     *
     * @param chatId идентификатор чата
     * @param messageText текст сообщения пользователя
     * @return сообщение следующего шага или итог диалога
     */
    public String consume(long chatId, String messageText) {
        TrackState state = activeDialogs.get(chatId);
        if (state == null) {
            return BotMessages.CANCEL_NO_ACTIVE;
        }

        if (state.stage == TrackDialogStage.AWAIT_LINK) {
            if (!inputParser.isValidUrl(messageText)) {
                return BotMessages.TRACK_INVALID_LINK;
            }
            state.link = messageText.trim();
            state.stage = TrackDialogStage.AWAIT_TAGS;
            return BotMessages.TRACK_ENTER_TAGS;
        }

        if (state.stage == TrackDialogStage.AWAIT_TAGS) {
            state.tags = inputParser.parseCsvValues(messageText);
            state.stage = TrackDialogStage.AWAIT_FILTERS;
            return BotMessages.TRACK_ENTER_FILTERS;
        }

        List<String> filters = inputParser.parseCsvValues(messageText);
        TrackSubmissionResult submitResult = submissionService.submit(chatId, state.link, state.tags, filters);
        activeDialogs.remove(chatId);
        return submitResult.responseMessage();
    }

    /**
     * Внутреннее состояние диалога для конкретного чата.
     */
    static final class TrackState {
        /**
         * Текущий шаг диалога.
         */
        private TrackDialogStage stage;
        /**
         * Введенная пользователем ссылка.
         */
        private String link;
        /**
         * Введенные пользователем теги.
         */
        private List<String> tags;

        /**
         * Создает состояние диалога с заданной стадией.
         *
         * @param stage стартовая стадия диалога
         */
        private TrackState(TrackDialogStage stage) {
            this.stage = stage;
        }

        /**
         * Создает начальное состояние для нового диалога.
         *
         * @return состояние с шагом ввода ссылки
         */
        private static TrackState initial() {
            return new TrackState(TrackDialogStage.AWAIT_LINK);
        }
    }
}
