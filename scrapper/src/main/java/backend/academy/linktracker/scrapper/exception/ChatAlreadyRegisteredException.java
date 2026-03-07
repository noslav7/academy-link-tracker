package backend.academy.linktracker.scrapper.exception;

/**
 * Ошибка повторной регистрации уже существующего Telegram-чата.
 */
public class ChatAlreadyRegisteredException extends RuntimeException {
    /**
     * Создает исключение о повторной регистрации чата.
     *
     * @param chatId идентификатор чата
     */
    public ChatAlreadyRegisteredException(long chatId) {
        super("Chat is already registered: " + chatId);
    }
}
