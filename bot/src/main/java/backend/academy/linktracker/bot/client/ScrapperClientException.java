package backend.academy.linktracker.bot.client;

/**
 * Базовое исключение ошибок взаимодействия с scrapper-клиентом.
 */
public class ScrapperClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Создает исключение с сообщением.
     *
     * @param message текст ошибки
     */
    public ScrapperClientException(String message) {
        super(requireMessage(message));
    }

    /**
     * Создает исключение с сообщением и причиной.
     *
     * @param message текст ошибки
     * @param cause первопричина ошибки
     */
    public ScrapperClientException(String message, Throwable cause) {
        super(requireMessage(message), cause);
    }

    /**
     * Проверяет текст ошибки на пустое значение.
     *
     * @param message текст ошибки
     * @return валидный текст ошибки
     * @implNote Нужно, чтобы исключения всегда содержали диагностически полезное сообщение.
     */
    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message;
    }
}
