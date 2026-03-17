package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.AddTagRequest;
import backend.academy.linktracker.scrapper.dto.ListTagsResponse;
import backend.academy.linktracker.scrapper.dto.RemoveTagRequest;
import backend.academy.linktracker.scrapper.dto.UpdateTagRequest;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
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

    private static final int MAX_TAG_LENGTH = 128;
    private static final int MAX_LINK_LENGTH = 2048;

    private final TrackedLinkRepository repository;

    public TagService(TrackedLinkRepository repository) {
        this.repository = repository;
    }

    public ListTagsResponse list(long chatId, String link) {
        ensureChatExists(chatId);
        String normalizedLink = normalizeLink(link);
        ensureLinkExists(chatId, normalizedLink);
        List<String> tags = repository.findTagsByChatIdAndUrl(chatId, normalizedLink);
        return new ListTagsResponse(tags, tags.size());
    }

    public void add(long chatId, AddTagRequest request) {
        ensureChatExists(chatId);
        String normalizedLink = normalizeLink(request.link());
        ensureLinkExists(chatId, normalizedLink);
        String normalizedTag = normalizeTag(request.tag());
        if (!repository.addTag(chatId, normalizedLink, normalizedTag)) {
            throw new TagAlreadyExistsException(normalizedTag);
        }
    }

    public void update(long chatId, UpdateTagRequest request) {
        ensureChatExists(chatId);
        String normalizedLink = normalizeLink(request.link());
        ensureLinkExists(chatId, normalizedLink);
        String normalizedOldTag = normalizeTag(request.oldTag());
        String normalizedNewTag = normalizeTag(request.newTag());
        if (!repository.updateTag(chatId, normalizedLink, normalizedOldTag, normalizedNewTag)) {
            if (repository.findTagsByChatIdAndUrl(chatId, normalizedLink).contains(normalizedNewTag)) {
                throw new TagAlreadyExistsException(normalizedNewTag);
            }
            throw new TagNotFoundException(normalizedOldTag);
        }
    }

    public void delete(long chatId, RemoveTagRequest request) {
        ensureChatExists(chatId);
        String normalizedLink = normalizeLink(request.link());
        ensureLinkExists(chatId, normalizedLink);
        String normalizedTag = normalizeTag(request.tag());
        if (!repository.deleteTag(chatId, normalizedLink, normalizedTag)) {
            throw new TagNotFoundException(normalizedTag);
        }
    }

    private void ensureChatExists(long chatId) {
        if (!repository.chatExists(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
    }

    private void ensureLinkExists(long chatId, String link) {
        repository.findByChatIdAndUrl(chatId, link).orElseThrow(() -> new LinkNotFoundException(link));
    }

    private static String normalizeTag(String rawTag) {
        if (rawTag == null || rawTag.isBlank()) {
            throw new IllegalArgumentException("tag is required");
        }
        String normalizedTag = rawTag.trim();
        if (normalizedTag.length() > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException("tag length exceeds " + MAX_TAG_LENGTH + " characters");
        }
        return normalizedTag;
    }

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
