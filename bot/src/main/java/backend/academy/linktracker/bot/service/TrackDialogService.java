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
     * Активные сессии диалога по идентификатору чата.
     */
    private final Map<Long, TrackDialogSession> activeDialogs = new ConcurrentHashMap<>();
    /**
     * Компонент валидации и разбора текстового ввода пользователя.
     */
    private final TrackDialogInputParser inputParser;
    /**
     * Компонент отправки подготовленных данных в scrapper.
     */
    private final TrackDialogSubmissionService submissionService;
    /**
     * Компонент сопоставления результата сабмита и текста ответа пользователю.
     */
    private final TrackSubmissionResultMessageResolver resultMessageResolver;

    /**
     * Создает сервис управления диалогом команды {@code /track}.
     *
     * @param inputParser компонент валидации пользовательского ввода
     * @param submissionService компонент отправки данных в scrapper
     */
    public TrackDialogService(
            TrackDialogInputParser inputParser,
            TrackDialogSubmissionService submissionService,
            TrackSubmissionResultMessageResolver resultMessageResolver) {
        this.inputParser = inputParser;
        this.submissionService = submissionService;
        this.resultMessageResolver = resultMessageResolver;
    }

    /**
     * Начинает новый диалог отслеживания для чата.
     *
     * @param chatId идентификатор чата
     * @return текст следующего шага диалога
     * @implNote Нужно, чтобы при повторном запуске `/track` сбрасывать незавершенную сессию и начать сценарий
     *     заново в предсказуемом состоянии.
     */
    public String start(long chatId) {
        activeDialogs.put(chatId, TrackDialogSession.initial());
        return BotMessages.TRACK_ENTER_LINK;
    }

    /**
     * Проверяет, запущен ли диалог {@code /track} в указанном чате.
     *
     * @param chatId идентификатор чата
     * @return {@code true}, если диалог активен
     * @implNote Нужно, чтобы обработчик команд корректно различал обычные сообщения и шаги активного диалога.
     */
    public boolean isActive(long chatId) {
        return activeDialogs.containsKey(chatId);
    }

    /**
     * Прерывает активный диалог в чате.
     *
     * @param chatId идентификатор чата
     * @return сообщение о результате отмены
     * @implNote Нужно, чтобы пользователь мог безопасно выйти из сценария и не отправлять устаревшие данные в
     *     scrapper.
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
     * @implNote Нужно, чтобы вся логика пошагового сценария была централизована в одном месте и не дублировалась
     *     в обработчиках команд.
     */
    public String consume(long chatId, String messageText) {
        TrackDialogSession session = activeDialogs.get(chatId);
        if (session == null) {
            return BotMessages.CANCEL_NO_ACTIVE;
        }

        if (session.stage == TrackDialogStage.AWAIT_LINK) {
            if (!inputParser.isValidUrl(messageText)) {
                return BotMessages.TRACK_INVALID_LINK;
            }
            session.link = messageText.trim();
            session.stage = TrackDialogStage.AWAIT_TAGS;
            return BotMessages.TRACK_ENTER_TAGS;
        }

        if (session.stage == TrackDialogStage.AWAIT_TAGS) {
            session.tags = inputParser.parseCsvValues(messageText);
            session.stage = TrackDialogStage.AWAIT_FILTERS;
            return BotMessages.TRACK_ENTER_FILTERS;
        }

        List<String> filters = inputParser.parseCsvValues(messageText);
        TrackSubmissionResult submitResult = submissionService.submit(chatId, session.link, session.tags, filters);
        activeDialogs.remove(chatId);
        return resultMessageResolver.resolve(submitResult);
    }

    /**
     * Внутренняя сессия диалога для конкретного чата.
     */
    static final class TrackDialogSession {
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
         * Создает сессию диалога с заданной стадией.
         *
         * @param stage стартовая стадия диалога
         * @implNote Нужно, чтобы гарантировать создание сессии только в корректной начальной/промежуточной стадии.
         */
        private TrackDialogSession(TrackDialogStage stage) {
            this.stage = stage;
        }

        /**
         * Создает начальную сессию для нового диалога.
         *
         * @return состояние с шагом ввода ссылки
         * @implNote Нужно, чтобы все новые сценарии `/track` стартовали единообразно с этапа ввода ссылки.
         */
        private static TrackDialogSession initial() {
            return new TrackDialogSession(TrackDialogStage.AWAIT_LINK);
        }
    }
}
