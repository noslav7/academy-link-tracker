package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления отслеживаемыми ссылками для Telegram-чатов.
 */
@Service
public class LinkService {

    /**
     * Репозиторий хранения чатов и ссылок.
     */
    private final TrackedLinkRepository repository;
    /**
     * Резолвер метаданных внешних ссылок.
     */
    private final LinkMetadataResolver linkMetadataResolver;
    /**
     * Переиспользуемые проверки доступа к чату и подписке.
     */
    private final ChatLinkAccessValidator accessValidator;

    /**
     * Создает сервис управления ссылками.
     *
     * @param repository репозиторий хранения ссылок
     * @param linkMetadataResolver резолвер времени обновления внешних ссылок
     * @param accessValidator проверки доступа к чату и подписке
     */
    public LinkService(
            TrackedLinkRepository repository,
            LinkMetadataResolver linkMetadataResolver,
            ChatLinkAccessValidator accessValidator) {
        this.repository = repository;
        this.linkMetadataResolver = linkMetadataResolver;
        this.accessValidator = accessValidator;
    }

    /**
     * Добавляет новую ссылку в отслеживание для чата.
     *
     * @param chatId идентификатор чата
     * @param request данные ссылки, тегов и фильтров
     * @return добавленная ссылка в формате API-ответа
     */
    @Transactional
    public LinkResponse add(long chatId, AddLinkRequest request) {
        accessValidator.ensureChatExists(chatId);

        String normalizedLink = RequestFieldNormalizer.normalizeLink(request.link());
        repository.findByChatIdAndUrl(chatId, normalizedLink).ifPresent(link -> {
            throw new LinkAlreadyTrackedException(normalizedLink);
        });

        Instant lastUpdated = linkMetadataResolver.resolveLastUpdated(normalizedLink);
        TrackedLink saved = repository.save(
                chatId,
                normalizedLink,
                request.tags() == null ? List.of() : request.tags(),
                request.filters() == null ? List.of() : request.filters(),
                lastUpdated);
        return toResponse(saved);
    }

    /**
     * Удаляет ссылку из отслеживания для чата.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для удаления
     * @return удаленная ссылка в формате API-ответа
     */
    @Transactional
    public LinkResponse remove(long chatId, String link) {
        accessValidator.ensureChatExists(chatId);
        String normalizedLink = RequestFieldNormalizer.normalizeLink(link);
        TrackedLink existing = accessValidator.ensureLinkExists(chatId, normalizedLink);
        repository.deleteByChatIdAndUrl(chatId, normalizedLink);
        return toResponse(existing);
    }

    /**
     * Возвращает все отслеживаемые ссылки указанного чата.
     *
     * @param chatId идентификатор чата
     * @return список ссылок в формате API-ответа
     */
    public List<LinkResponse> list(long chatId) {
        accessValidator.ensureChatExists(chatId);
        return repository.findAllByChatId(chatId).stream()
                .map(LinkService::toResponse)
                .toList();
    }

    /**
     * Преобразует доменную сущность в API-ответ.
     *
     * @param trackedLink доменная сущность отслеживаемой ссылки
     * @return API-представление ссылки
     */
    private static LinkResponse toResponse(TrackedLink trackedLink) {
        return new LinkResponse(trackedLink.id(), trackedLink.url(), trackedLink.tags(), trackedLink.filters());
    }
}
