package backend.academy.linktracker.scrapper.exception;

/**
 * Ошибка обращения к несуществующему Telegram-чату.
 */
public class ChatNotFoundException extends RuntimeException {
    /**
     * Создает исключение отсутствующего чата.
     *
     * @param chatId идентификатор чата
     */
    public ChatNotFoundException(long chatId) {
        super("Chat not found: " + chatId);
    }
}
