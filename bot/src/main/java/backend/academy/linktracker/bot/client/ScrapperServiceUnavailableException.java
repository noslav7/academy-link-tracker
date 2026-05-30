package backend.academy.linktracker.bot.client;

/**
 * Транспортная ошибка недоступности scrapper-сервиса.
 */
public class ScrapperServiceUnavailableException extends ScrapperClientException {
    private static final long serialVersionUID = 1L;

    /**
     * Создает исключение недоступности сервиса с сообщением.
     *
     * @param message текст ошибки
     */
    public ScrapperServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * Создает исключение недоступности сервиса с причиной.
     *
     * @param message текст ошибки
     * @param cause первопричина
     */
    public ScrapperServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
