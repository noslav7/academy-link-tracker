package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.exception.ChatAlreadyRegisteredException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import org.springframework.stereotype.Service;

/**
 * Сервис регистрации и удаления Telegram-чатов в scrapper.
 */
@Service
public class ChatService {

    /**
     * Репозиторий хранения чатов и ссылок.
     */
    private final TrackedLinkRepository repository;

    /**
     * Создает сервис управления чатами.
     *
     * @param repository репозиторий хранения чатов
     */
    public ChatService(TrackedLinkRepository repository) {
        this.repository = repository;
    }

    /**
     * Регистрирует новый чат.
     *
     * @param chatId идентификатор чата
     */
    public void register(long chatId) {
        boolean created = repository.registerChat(chatId);
        if (!created) {
            throw new ChatAlreadyRegisteredException(chatId);
        }
    }

    /**
     * Удаляет существующий чат.
     *
     * @param chatId идентификатор чата
     */
    public void remove(long chatId) {
        boolean deleted = repository.deleteChat(chatId);
        if (!deleted) {
            throw new ChatNotFoundException(chatId);
        }
    }
}
