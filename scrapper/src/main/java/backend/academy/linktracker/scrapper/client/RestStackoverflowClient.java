package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиент получения метаданных вопросов из Stack Overflow API.
 */
@Component
public class RestStackoverflowClient implements StackoverflowClient {
    /**
     * Значение параметра {@code site} для StackOverflow API.
     */
    private static final String SITE = "stackoverflow";
    /**
     * Резервное имя автора события, используемое при пустом имени в ответе API.
     */
    private static final String UNKNOWN_AUTHOR = "unknown";
    /**
     * Резервный заголовок вопроса, используемый для корректного текста уведомления.
     */
    private static final String UNTITLED_QUESTION = "Без названия";

    /**
     * Клиент HTTP-запросов к Stack Overflow API.
     */
    private final RestClient restClient;
    /**
     * Настройки подключения к Stack Overflow API.
     */
    private final StackoverflowProperties properties;
    /**
     * Единый блок устойчивости HTTP-вызовов (retry).
     */
    private final ResilientHttpExecutor resilientHttpExecutor;

    /**
     * Создает REST-клиент Stack Overflow API.
     *
     * @param restClient HTTP-клиент Stack Overflow
     * @param properties настройки подключения
     * @param resilientHttpExecutor компонент применения retry/fallback-политики
     */
    public RestStackoverflowClient(
            @Qualifier("stackoverflowRestClient") RestClient restClient,
            StackoverflowProperties properties,
            ResilientHttpExecutor resilientHttpExecutor) {
        this.restClient = restClient;
        this.properties = properties;
        this.resilientHttpExecutor = resilientHttpExecutor;
    }

    /**
     * Получает время последней активности вопроса.
     * Нужно для быстрой проверки изменений вопроса без загрузки событий.
     *
     * @param questionId идентификатор вопроса
     * @return время последней активности или {@link Instant#EPOCH} при невалидном входе/ошибке API
     */
    @Override
    public Instant fetchQuestionUpdatedAt(long questionId) {
        if (!isValidQuestionId(questionId)) {
            return Instant.EPOCH;
        }
        LinkedMultiValueMap<String, String> params = defaultParams();

        return resilientHttpExecutor.executeOrFallback(
                () -> extractQuestionUpdatedAtOrEpoch(fetchResponse("/questions/{id}", params, questionId)),
                () -> Instant.EPOCH);
    }

    /**
     * Получает последний ответ или комментарий для вопроса.
     * Нужно для формирования информативного описания обновления.
     *
     * @param questionId идентификатор вопроса
     * @return данные последнего события или пустой результат при невалидном входе/ошибке API
     */
    @Override
    public Optional<StackoverflowAnswerOrCommentUpdate> fetchLatestAnswerOrCommentUpdate(long questionId) {
        if (!isValidQuestionId(questionId)) {
            return Optional.empty();
        }
        return resilientHttpExecutor.executeOrFallback(
                () -> extractLatestAnswerOrCommentUpdate(questionId), Optional::empty);
    }

    /**
     * Нормализует ответ вопроса к времени последней активности.
     * Нужен для единообразного fallback-поведения в одном месте.
     *
     * @param response ответ API по вопросу
     * @return время последней активности или {@link Instant#EPOCH}
     */
    private Instant extractQuestionUpdatedAtOrEpoch(StackoverflowResponse response) {
        Optional<StackoverflowItem> firstItem = firstItem(response);
        if (firstItem.isEmpty()) {
            return Instant.EPOCH;
        }
        Long lastActivity = firstItem.orElseThrow().lastActivityDate();
        return lastActivity == null ? Instant.EPOCH : Instant.ofEpochSecond(lastActivity);
    }

    /**
     * Собирает доменную модель последнего события (ответ/комментарий) по вопросу.
     * Нужен для разделения сетевых вызовов и логики выбора события.
     *
     * @param questionId идентификатор вопроса
     * @return модель последнего события либо пустой результат
     */
    private Optional<StackoverflowAnswerOrCommentUpdate> extractLatestAnswerOrCommentUpdate(long questionId) {
        LinkedMultiValueMap<String, String> questionParams = defaultParams();
        questionParams.add("filter", "withbody");
        StackoverflowResponse questionResponse = fetchResponse("/questions/{id}", questionParams, questionId);

        Optional<StackoverflowItem> questionItem = firstItem(questionResponse);
        if (questionItem.isEmpty()) {
            return Optional.empty();
        }
        StackoverflowItem question = questionItem.orElseThrow();
        String questionTitle = normalizeOrFallback(question.title(), UNTITLED_QUESTION);

        Optional<StackoverflowEvent> latestAnswer =
                fetchLatestEvent(questionId, "/questions/{id}/answers", StackoverflowEventType.ANSWER);
        Optional<StackoverflowEvent> latestComment =
                fetchLatestEvent(questionId, "/questions/{id}/comments", StackoverflowEventType.COMMENT);

        Optional<StackoverflowEvent> latestEvent = selectLatest(latestAnswer, latestComment);
        if (latestEvent.isEmpty()) {
            return Optional.empty();
        }
        StackoverflowEvent event = latestEvent.orElseThrow();
        return Optional.of(new StackoverflowAnswerOrCommentUpdate(
                questionTitle,
                event.type() == StackoverflowEventType.ANSWER,
                event.authorDisplayName(),
                event.createdAt(),
                event.body()));
    }

    /**
     * Загружает последнее событие (ответ или комментарий) по указанному endpoint.
     * Нужен как единый шаг чтения события для двух разных endpoint'ов API.
     *
     * @param questionId идентификатор вопроса
     * @param path путь API для получения события
     * @param eventType тип события (ответ или комментарий)
     * @return найденное событие или пустой результат
     */
    private Optional<StackoverflowEvent> fetchLatestEvent(
            long questionId, String path, StackoverflowEventType eventType) {
        StackoverflowResponse response = fetchResponse(path, latestEventParams(), questionId);

        Optional<StackoverflowItem> firstItem = firstItem(response);
        if (firstItem.isEmpty()) {
            return Optional.empty();
        }
        StackoverflowItem item = firstItem.orElseThrow();
        if (item.creationDate() == null) {
            return Optional.empty();
        }
        return Optional.of(new StackoverflowEvent(
                eventType,
                normalizeOrFallback(item.owner() == null ? null : item.owner().displayName(), UNKNOWN_AUTHOR),
                Instant.ofEpochSecond(item.creationDate()),
                item.body() == null ? "" : item.body()));
    }

    /**
     * Возвращает первый элемент ответа StackOverflow API, если он присутствует.
     * Нужен для централизованной обработки пустых/неполных API-ответов.
     *
     * @param response ответ StackOverflow API
     * @return первый элемент ответа или пустой результат
     */
    private static Optional<StackoverflowItem> firstItem(StackoverflowResponse response) {
        if (response == null || response.items() == null || response.items().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.items().getFirst());
    }

    /**
     * Выбирает более позднее событие из пары «последний ответ / последний комментарий».
     * Нужен для корректного выбора одного события для итогового уведомления.
     *
     * @param answer последнее событие-ответ
     * @param comment последнее событие-комментарий
     * @return наиболее позднее событие из переданных
     */
    private static Optional<StackoverflowEvent> selectLatest(
            Optional<StackoverflowEvent> answer, Optional<StackoverflowEvent> comment) {
        if (answer.isEmpty()) {
            return comment;
        }
        if (comment.isEmpty()) {
            return answer;
        }
        StackoverflowEvent answerEvent = answer.orElseThrow();
        StackoverflowEvent commentEvent = comment.orElseThrow();
        return answerEvent.createdAt().isAfter(commentEvent.createdAt()) ? answer : comment;
    }

    /**
     * Собирает базовые query-параметры для запросов к StackOverflow API.
     * Нужен как единая точка формирования обязательных и опциональных ключей доступа.
     *
     * @return набор параметров с обязательным {@code site} и опциональными ключами доступа
     */
    private LinkedMultiValueMap<String, String> defaultParams() {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("site", SITE);
        if (properties.getKey() != null && !properties.getKey().isBlank()) {
            params.add("key", properties.getKey());
        }
        if (properties.getAccessToken() != null && !properties.getAccessToken().isBlank()) {
            params.add("access_token", properties.getAccessToken());
        }
        return params;
    }

    /**
     * Собирает query-параметры для получения последнего ответа/комментария.
     * Нужен для переиспользования одной и той же сортировки/фильтрации в двух endpoint'ах.
     *
     * @return параметры запроса последних событий
     */
    private LinkedMultiValueMap<String, String> latestEventParams() {
        LinkedMultiValueMap<String, String> params = defaultParams();
        params.add("order", "desc");
        params.add("sort", "creation");
        params.add("filter", "withbody");
        params.add("pagesize", "1");
        return params;
    }

    /**
     * Выполняет HTTP-запрос к StackOverflow API и возвращает тело ответа.
     * Нужен как единая точка запроса, чтобы не дублировать сетевую цепочку.
     *
     * @param path путь API
     * @param params query-параметры запроса
     * @param questionId идентификатор вопроса для подстановки в путь
     * @return распарсенный ответ API
     */
    private StackoverflowResponse fetchResponse(
            String path, LinkedMultiValueMap<String, String> params, long questionId) {
        if (!hasTextValue(path)) {
            throw new IllegalArgumentException("StackOverflow API path must not be blank");
        }
        return resilientHttpExecutor.execute(() -> restClient
                .get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParams(params).build(questionId))
                .retrieve()
                .body(StackoverflowResponse.class));
    }

    /**
     * Проверяет корректность идентификатора вопроса.
     * Нужен для раннего отклонения заведомо невалидного входа.
     *
     * @param questionId идентификатор вопроса
     * @return {@code true}, если идентификатор положительный
     */
    private static boolean isValidQuestionId(long questionId) {
        return questionId > 0;
    }

    /**
     * Возвращает нормализованное строковое значение либо fallback.
     * Нужен для устранения null/blank значений из внешнего API.
     *
     * @param value исходное значение
     * @param fallback значение по умолчанию
     * @return исходное значение или fallback
     */
    private static String normalizeOrFallback(String value, String fallback) {
        if (!hasTextValue(value)) {
            return fallback;
        }
        return value;
    }

    /**
     * Проверяет, что строка содержит непустой текст.
     * Нужен как переиспользуемая проверка в локальных хелперах.
     *
     * @param value строковое значение
     * @return {@code true}, если строка не null и не blank
     */
    private static boolean hasTextValue(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * DTO ответа Stack Overflow API.
     * Нужен для чтения списка элементов без привязки к полной схеме внешнего API.
     *
     * @param items список элементов ответа
     */
    private record StackoverflowResponse(List<StackoverflowItem> items) {}

    /**
     * DTO элемента ответа Stack Overflow API.
     * Нужен для маппинга минимального набора полей, используемых в уведомлениях.
     *
     * @param lastActivityDate время последней активности в epoch seconds
     */
    private record StackoverflowItem(
            @JsonProperty("last_activity_date") Long lastActivityDate,
            @JsonProperty("creation_date") Long creationDate,
            String title,
            String body,
            StackoverflowOwner owner) {}

    /**
     * DTO автора события из Stack Overflow API.
     * Нужен для извлечения отображаемого имени автора.
     */
    private record StackoverflowOwner(
            @JsonProperty("display_name") String displayName) {}

    /**
     * Тип события для определения, является ли обновление ответом или комментарием.
     * Нужен для исключения неочевидных boolean-флагов в промежуточной модели.
     */
    private enum StackoverflowEventType {
        ANSWER,
        COMMENT
    }

    /**
     * Внутренняя модель события ответа/комментария для выбора более позднего события.
     * Нужна как нейтральный слой между API DTO и итоговой доменной моделью обновления.
     */
    private record StackoverflowEvent(
            StackoverflowEventType type, String authorDisplayName, Instant createdAt, String body) {}
}
