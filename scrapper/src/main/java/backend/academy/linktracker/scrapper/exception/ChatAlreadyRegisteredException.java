package backend.academy.linktracker.scrapper.exception;

public class ChatAlreadyRegisteredException extends RuntimeException {
    public ChatAlreadyRegisteredException(long chatId) {
        super("Chat is already registered: " + chatId);
    }
}
