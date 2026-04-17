package backend.academy.linktracker.bot.kafka;

/**
 * Ошибка валидации входящего LinkUpdate-пейлоуда.
 */
public class InvalidLinkUpdateException extends RuntimeException {

    /**
     * Создает исключение валидации сообщения LinkUpdate.
     *
     * @param message текст ошибки
     */
    public InvalidLinkUpdateException(String message) {
        super(message);
    }
}
