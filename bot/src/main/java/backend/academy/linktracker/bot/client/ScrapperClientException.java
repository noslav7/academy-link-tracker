package backend.academy.linktracker.bot.client;

/**
 * Базовое исключение ошибок взаимодействия с scrapper-клиентом.
 */
public class ScrapperClientException extends RuntimeException {
    /**
     * Создает исключение с сообщением.
     *
     * @param message текст ошибки
     */
    public ScrapperClientException(String message) {
        super(message);
    }

    /**
     * Создает исключение с сообщением и причиной.
     *
     * @param message текст ошибки
     * @param cause первопричина ошибки
     */
    public ScrapperClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
