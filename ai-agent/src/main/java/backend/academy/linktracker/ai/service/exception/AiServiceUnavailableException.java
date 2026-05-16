package backend.academy.linktracker.ai.service.exception;

/**
 * Ошибка транспортного уровня при обращении к внешнему AI API.
 */
public class AiServiceUnavailableException extends RuntimeException {

    /**
     * Версия сериализации исключения.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Создает исключение недоступности интеграции с описанием причины.
     *
     * @param message текст причины
     */
    public AiServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * Создает исключение недоступности интеграции с первопричиной.
     *
     * @param message текст причины
     * @param cause первопричина ошибки транспорта
     */
    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
