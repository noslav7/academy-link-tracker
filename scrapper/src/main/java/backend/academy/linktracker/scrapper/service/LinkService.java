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

@Service
public class LinkService {

    private final TrackedLinkRepository repository;
    private final LinkMetadataResolver linkMetadataResolver;

    public LinkService(TrackedLinkRepository repository, LinkMetadataResolver linkMetadataResolver) {
        this.repository = repository;
        this.linkMetadataResolver = linkMetadataResolver;
    }

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

    public LinkResponse remove(long chatId, String link) {
        ensureChatExists(chatId);
        String normalizedLink = normalizeLink(link);
        TrackedLink existing = repository
                .findByChatIdAndUrl(chatId, normalizedLink)
                .orElseThrow(() -> new LinkNotFoundException(normalizedLink));
        repository.deleteByChatIdAndUrl(chatId, normalizedLink);
        return toResponse(existing);
    }

    public List<LinkResponse> list(long chatId) {
        ensureChatExists(chatId);
        return repository.findAllByChatId(chatId).stream()
                .map(LinkService::toResponse)
                .toList();
    }

    public List<TrackedLink> allTrackedLinks() {
        return repository.findAllChatIds().stream()
                .flatMap(chatId -> repository.findAllByChatId(chatId).stream())
                .toList();
    }

    public Instant resolveLastUpdated(String link) {
        return linkMetadataResolver.resolveLastUpdated(link);
    }

    public void updateTrackedLink(TrackedLink trackedLink) {
        repository.updateTrackedLink(trackedLink);
    }

    private void ensureChatExists(long chatId) {
        if (!repository.chatExists(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
    }

    private static LinkResponse toResponse(TrackedLink trackedLink) {
        return new LinkResponse(trackedLink.id(), trackedLink.url(), trackedLink.tags(), trackedLink.filters());
    }

    private static String normalizeLink(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            throw new IllegalArgumentException("link is required");
        }
        return rawLink.trim();
    }
}
