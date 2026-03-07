package backend.academy.linktracker.scrapper.client;

import java.time.Instant;

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
}
