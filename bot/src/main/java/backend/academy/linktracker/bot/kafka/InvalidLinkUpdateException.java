package backend.academy.linktracker.bot.kafka;

/**
 * Ошибка валидации входящего LinkUpdate-пейлоуда.
 */
public class InvalidLinkUpdateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Создает исключение валидации сообщения LinkUpdate.
     *
     * @param message текст ошибки
     */
    public InvalidLinkUpdateException(String message) {
        super(requireMessage(message));
    }

    /**
     * Проверяет текст ошибки на пустое значение.
     *
     * @param message текст ошибки
     * @return валидный текст ошибки
     * @implNote Нужно, чтобы сообщение валидационной ошибки оставалось информативным для логов и DLQ-диагностики.
     */
    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return message;
    }
}
