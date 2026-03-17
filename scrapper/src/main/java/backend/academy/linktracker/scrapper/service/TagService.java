package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.AddTagRequest;
import backend.academy.linktracker.scrapper.dto.ListTagsResponse;
import backend.academy.linktracker.scrapper.dto.RemoveTagRequest;
import backend.academy.linktracker.scrapper.dto.UpdateTagRequest;
import backend.academy.linktracker.scrapper.exception.TagAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.TagNotFoundException;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Сервис CRUD-операций управления тегами подписок.
 */
@Service
public class TagService {

    /**
     * Репозиторий хранения подписок и тегов.
     */
    private final TrackedLinkRepository repository;
    /**
     * Переиспользуемые проверки доступа к чату и подписке.
     */
    private final ChatLinkAccessValidator accessValidator;

    /**
     * Создает сервис управления тегами подписок.
     *
     * @param repository репозиторий хранения подписок
     * @param accessValidator проверки доступа к чату и подписке
     */
    public TagService(TrackedLinkRepository repository, ChatLinkAccessValidator accessValidator) {
        this.repository = repository;
        this.accessValidator = accessValidator;
    }

    /**
     * Возвращает список тегов для конкретной подписки чата.
     *
     * @param chatId идентификатор чата
     * @param link URL отслеживаемой ссылки
     * @return список тегов и их количество
     */
    public ListTagsResponse list(long chatId, String link) {
        accessValidator.ensureChatExists(chatId);
        String normalizedLink = RequestFieldNormalizer.normalizeLink(link);
        accessValidator.ensureLinkExists(chatId, normalizedLink);
        List<String> tags = repository.findTagsByChatIdAndUrl(chatId, normalizedLink);
        return new ListTagsResponse(tags, tags.size());
    }

    /**
     * Добавляет новый тег к существующей подписке.
     *
     * @param chatId идентификатор чата
     * @param request запрос добавления тега
     */
    public void add(long chatId, AddTagRequest request) {
        accessValidator.ensureChatExists(chatId);
        String normalizedLink = RequestFieldNormalizer.normalizeLink(request.link());
        accessValidator.ensureLinkExists(chatId, normalizedLink);
        String normalizedTag = RequestFieldNormalizer.normalizeTag(request.tag());
        if (!repository.addTag(chatId, normalizedLink, normalizedTag)) {
            throw new TagAlreadyExistsException(normalizedTag);
        }
    }

    /**
     * Обновляет значение существующего тега подписки.
     *
     * @param chatId идентификатор чата
     * @param request запрос обновления тега
     */
    public void update(long chatId, UpdateTagRequest request) {
        accessValidator.ensureChatExists(chatId);
        String normalizedLink = RequestFieldNormalizer.normalizeLink(request.link());
        accessValidator.ensureLinkExists(chatId, normalizedLink);
        String normalizedOldTag = RequestFieldNormalizer.normalizeTag(request.oldTag());
        String normalizedNewTag = RequestFieldNormalizer.normalizeTag(request.newTag());
        if (!repository.updateTag(chatId, normalizedLink, normalizedOldTag, normalizedNewTag)) {
            if (repository.findTagsByChatIdAndUrl(chatId, normalizedLink).contains(normalizedNewTag)) {
                throw new TagAlreadyExistsException(normalizedNewTag);
            }
            throw new TagNotFoundException(normalizedOldTag);
        }
    }

    /**
     * Удаляет тег у существующей подписки.
     *
     * @param chatId идентификатор чата
     * @param request запрос удаления тега
     */
    public void delete(long chatId, RemoveTagRequest request) {
        accessValidator.ensureChatExists(chatId);
        String normalizedLink = RequestFieldNormalizer.normalizeLink(request.link());
        accessValidator.ensureLinkExists(chatId, normalizedLink);
        String normalizedTag = RequestFieldNormalizer.normalizeTag(request.tag());
        if (!repository.deleteTag(chatId, normalizedLink, normalizedTag)) {
            throw new TagNotFoundException(normalizedTag);
        }
    }
}
