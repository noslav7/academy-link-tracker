package backend.academy.linktracker.scrapper.client;

/**
 * Транспортная ошибка недоступности bot-сервиса.
 */
public class BotServiceUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Создает исключение недоступности bot-сервиса.
     *
     * @param message текст ошибки
     */
    public BotServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * Создает исключение недоступности bot-сервиса с первопричиной.
     *
     * @param message текст ошибки
     * @param cause первопричина
     */
    public BotServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
