package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.StackoverflowClient;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
/**
 * Резолвер метаданных для ссылок на вопросы StackOverflow.
 */
public class StackoverflowLinkMetadataResolver extends AbstractHostBasedLinkMetadataResolver {

    /**
     * Часть имени хоста, указывающая на StackOverflow.
     */
    private static final String STACKOVERFLOW_HOST_PART = "stackoverflow.com";
    /**
     * Индекс сегмента идентификатора вопроса в URL-пути.
     */
    private static final int QUESTION_PATH_SEGMENT_INDEX = 1;
    /**
     * Минимальное количество сегментов пути для ссылки на вопрос.
     */
    private static final int MIN_SEGMENTS_COUNT = 2;

    /**
     * Клиент StackOverflow API.
     */
    private final StackoverflowClient stackoverflowClient;

    /**
     * Создает резолвер ссылок StackOverflow.
     *
     * @param stackoverflowClient клиент доступа к StackOverflow API
     */
    public StackoverflowLinkMetadataResolver(StackoverflowClient stackoverflowClient) {
        super(STACKOVERFLOW_HOST_PART, MIN_SEGMENTS_COUNT);
        this.stackoverflowClient = stackoverflowClient;
    }

    /**
     * Проверяет структуру пути URI для формата ссылки на вопрос StackOverflow.
     *
     * @param pathSegments сегменты пути URI
     * @return {@code true}, если формат ссылки поддерживается
     */
    @Override
    protected boolean supportsPathSegments(List<String> pathSegments) {
        return extractQuestionId(pathSegments) != null;
    }

    /**
     * Получает время последнего обновления вопроса StackOverflow.
     *
     * @param uri ссылка в формате URI
     * @return время последнего обновления вопроса
     */
    @Override
    public Instant resolveLastUpdated(URI uri) {
        Long questionId = extractQuestionId(extractPathSegments(uri));
        if (questionId == null) {
            throw new IllegalStateException("Unsupported StackOverflow URI: " + uri);
        }
        return stackoverflowClient.fetchQuestionUpdatedAt(questionId);
    }

    /**
     * Извлекает идентификатор вопроса из сегментов URL-пути.
     *
     * @param segments сегменты пути URI
     * @return идентификатор вопроса или {@code null}, если путь невалиден
     */
    private static Long extractQuestionId(List<String> segments) {
        if (segments.size() < MIN_SEGMENTS_COUNT) {
            return null;
        }
        String prefix = segments.getFirst();
        if (StackoverflowQuestionPathPrefix.fromSymbol(prefix).isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(segments.get(QUESTION_PATH_SEGMENT_INDEX));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
