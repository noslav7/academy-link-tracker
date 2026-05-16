package backend.academy.linktracker.ai.service.exception;

/**
 * Доменная ошибка бизнес-уровня при суммаризации через внешний AI API.
 */
public class AiSummarizationBusinessException extends RuntimeException {

    /**
     * Версия сериализации исключения.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Создает бизнес-ошибку суммаризации с описанием причины.
     *
     * @param message текст причины
     */
    public AiSummarizationBusinessException(String message) {
        super(message);
    }

    /**
     * Создает бизнес-ошибку суммаризации с первопричиной.
     *
     * @param message текст причины
     * @param cause первопричина ошибки обработки
     */
    public AiSummarizationBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
