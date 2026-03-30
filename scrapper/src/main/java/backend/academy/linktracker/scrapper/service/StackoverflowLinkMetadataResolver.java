package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.StackoverflowClient;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Резолвер метаданных для ссылок на вопросы StackOverflow.
 */
@Component
public class StackoverflowLinkMetadataResolver extends AbstractHostBasedLinkMetadataResolver {
    private static final int MAX_PREVIEW_LENGTH = 200;

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

    @Override
    public Instant resolveLastUpdated(URI uri) {
        return stackoverflowClient.fetchQuestionUpdatedAt(requireQuestionId(uri));
    }

    @Override
    public String describeUpdate(URI uri) {
        long questionId = requireQuestionId(uri);
        return stackoverflowClient
                .fetchLatestAnswerOrCommentUpdate(questionId)
                .map(update -> """
                        StackOverflow: новый %s
                        Тема вопроса: %s
                        Пользователь: %s
                        Время создания: %s
                        Превью: %s
                        """
                        .formatted(
                                update.answer() ? "ответ" : "комментарий",
                                safe(update.questionTitle()),
                                safe(update.authorDisplayName()),
                                update.createdAt(),
                                toPreview(update.body())))
                .orElse("Обновление вопроса #%d на StackOverflow (возможно: новый ответ или комментарий)"
                        .formatted(questionId));
    }

    private long requireQuestionId(URI uri) {
        Long questionId = extractQuestionId(extractPathSegments(uri));
        if (questionId == null) {
            throw new IllegalStateException("Unsupported StackOverflow URI: " + uri);
        }
        return questionId;
    }

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

    private static String toPreview(String body) {
        String plainText = stripHtml(safe(body)).trim();
        if (plainText.isEmpty()) {
            return "(пусто)";
        }
        return plainText.substring(0, Math.min(MAX_PREVIEW_LENGTH, plainText.length()));
    }

    private static String stripHtml(String value) {
        return value.replaceAll("<[^>]*>", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
