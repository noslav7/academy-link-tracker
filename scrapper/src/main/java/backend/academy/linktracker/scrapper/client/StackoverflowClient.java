package backend.academy.linktracker.scrapper.client;

import java.time.Instant;
import java.util.Optional;

/**
 * Контракт клиента доступа к Stack Overflow API.
 */
public interface StackoverflowClient {
    /**
     * Возвращает время последней активности вопроса.
     *
     * @param questionId идентификатор вопроса
     * @return время последней активности
     */
    Instant fetchQuestionUpdatedAt(long questionId);

    /**
     * Возвращает сведения о последнем ответе или комментарии по вопросу.
     *
     * @param questionId идентификатор вопроса
     * @return данные последнего события, если удалось получить
     */
    default Optional<StackoverflowAnswerOrCommentUpdate> fetchLatestAnswerOrCommentUpdate(long questionId) {
        return Optional.empty();
    }

    /**
     * Детали последнего обновления по вопросу StackOverflow.
     *
     * @param questionTitle заголовок вопроса
     * @param answer тип события: ответ или комментарий
     * @param authorDisplayName отображаемое имя автора
     * @param createdAt время создания события
     * @param body текст ответа или комментария
     */
    record StackoverflowAnswerOrCommentUpdate(
            String questionTitle, boolean answer, String authorDisplayName, Instant createdAt, String body) {}
}
