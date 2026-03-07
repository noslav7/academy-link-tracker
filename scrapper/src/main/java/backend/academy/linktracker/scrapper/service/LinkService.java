package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Сервис управления отслеживаемыми ссылками для Telegram-чатов.
 */
@Service
public class LinkService {

    /**
     * Максимальная длина ссылки, принимаемой сервисом.
     */
    private static final int MAX_LINK_LENGTH = 2048;

    /**
     * Репозиторий хранения чатов и ссылок.
     */
    private final TrackedLinkRepository repository;
    /**
     * Резолвер метаданных внешних ссылок.
     */
    private final LinkMetadataResolver linkMetadataResolver;

    /**
     * Создает сервис управления ссылками.
     *
     * @param repository репозиторий хранения ссылок
     * @param linkMetadataResolver резолвер времени обновления внешних ссылок
     */
    public LinkService(TrackedLinkRepository repository, LinkMetadataResolver linkMetadataResolver) {
        this.repository = repository;
        this.linkMetadataResolver = linkMetadataResolver;
    }

    /**
     * Добавляет новую ссылку в отслеживание для чата.
     *
     * @param chatId идентификатор чата
     * @param request данные ссылки, тегов и фильтров
     * @return добавленная ссылка в формате API-ответа
     */
    public LinkResponse add(long chatId, AddLinkRequest request) {
        ensureChatExists(chatId);

        String normalizedLink = normalizeLink(request.link());
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
    public LinkResponse remove(long chatId, String link) {
        ensureChatExists(chatId);
        String normalizedLink = normalizeLink(link);
        TrackedLink existing = repository
                .findByChatIdAndUrl(chatId, normalizedLink)
                .orElseThrow(() -> new LinkNotFoundException(normalizedLink));
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
        ensureChatExists(chatId);
        return repository.findAllByChatId(chatId).stream()
                .map(LinkService::toResponse)
                .toList();
    }

    /**
     * Возвращает все отслеживаемые ссылки из всех чатов.
     *
     * @return список доменных сущностей отслеживаемых ссылок
     */
    public List<TrackedLink> allTrackedLinks() {
        return repository.findAllChatIds().stream()
                .flatMap(chatId -> repository.findAllByChatId(chatId).stream())
                .toList();
    }

    /**
     * Делегирует получение времени последнего обновления ссылки.
     *
     * @param link строковое представление ссылки
     * @return время последнего обновления ресурса
     */
    public Instant resolveLastUpdated(String link) {
        return linkMetadataResolver.resolveLastUpdated(link);
    }

    /**
     * Обновляет метаданные существующей отслеживаемой ссылки.
     *
     * @param trackedLink ссылка с новыми полями проверки/обновления
     */
    public void updateTrackedLink(TrackedLink trackedLink) {
        repository.updateTrackedLink(trackedLink);
    }

    /**
     * Проверяет, что чат существует в репозитории.
     *
     * @param chatId идентификатор чата
     */
    private void ensureChatExists(long chatId) {
        if (!repository.chatExists(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
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

    /**
     * Нормализует и валидирует строковое представление ссылки.
     *
     * @param rawLink исходная строка ссылки
     * @return нормализованная ссылка без лишних пробелов
     */
    private static String normalizeLink(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            throw new IllegalArgumentException("link is required");
        }
        String normalized = rawLink.trim();
        if (normalized.length() > MAX_LINK_LENGTH) {
            throw new IllegalArgumentException("link length exceeds " + MAX_LINK_LENGTH + " characters");
        }
        return normalized;
    }
}
