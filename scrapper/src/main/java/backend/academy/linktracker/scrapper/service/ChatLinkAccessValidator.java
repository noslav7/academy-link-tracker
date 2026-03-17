package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import org.springframework.stereotype.Component;

/**
 * Переиспользуемая проверка доступа к чату и отслеживаемой ссылке.
 */
@Component
public class ChatLinkAccessValidator {

    /**
     * Репозиторий хранения чатов и подписок.
     */
    private final TrackedLinkRepository repository;

    /**
     * Создает валидатор доступа к чату и ссылке.
     *
     * @param repository репозиторий хранения подписок
     */
    public ChatLinkAccessValidator(TrackedLinkRepository repository) {
        this.repository = repository;
    }

    /**
     * Проверяет, что чат зарегистрирован в системе.
     *
     * @param chatId идентификатор чата
     */
    public void ensureChatExists(long chatId) {
        if (!repository.chatExists(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
    }

    /**
     * Проверяет, что подписка на ссылку существует, и возвращает ее.
     *
     * @param chatId идентификатор чата
     * @param link URL отслеживаемой ссылки
     * @return найденная подписка
     */
    public TrackedLink ensureLinkExists(long chatId, String link) {
        return repository.findByChatIdAndUrl(chatId, link).orElseThrow(() -> new LinkNotFoundException(link));
    }
}
