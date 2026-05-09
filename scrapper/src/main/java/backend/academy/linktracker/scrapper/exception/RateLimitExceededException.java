package backend.academy.linktracker.scrapper.exception;

/**
 * Исключение, сигнализирующее о превышении лимита запросов.
 *
 * <p>Нужно, чтобы отделить бизнес-факт превышения квоты от транспортного
 * представления и централизованно преобразовать его в HTTP 429.
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * Создает исключение с диагностическим сообщением.
     *
     * <p>Нужно, чтобы в ответе и логах сохранялась причина отклонения запроса.
     *
     * @param message сообщение о причине превышения лимита
     */
    public RateLimitExceededException(String message) {
        super(message);
    }
}
