package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.exception.ChatAlreadyRegisteredException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final TrackedLinkRepository repository;

    public ChatService(TrackedLinkRepository repository) {
        this.repository = repository;
    }

    public void register(long chatId) {
        boolean created = repository.registerChat(chatId);
        if (!created) {
            throw new ChatAlreadyRegisteredException(chatId);
        }
    }

    public void remove(long chatId) {
        boolean deleted = repository.deleteChat(chatId);
        if (!deleted) {
            throw new ChatNotFoundException(chatId);
        }
    }
}
