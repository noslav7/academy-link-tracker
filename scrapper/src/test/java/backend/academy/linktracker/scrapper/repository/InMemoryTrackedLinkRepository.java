package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory реализация репозитория отслеживаемых ссылок для тестов.
 */
public class InMemoryTrackedLinkRepository extends AbstractPagingAwareTrackedLinkRepository
        implements TrackedLinkRepository {

    /**
     * Хранилище ссылок по chatId и URL.
     */
    private final Map<Long, Map<String, TrackedLink>> linksByChatId = new ConcurrentHashMap<>();
    /**
     * Генератор идентификаторов ссылок.
     */
    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean registerChat(long chatId) {
        return linksByChatId.putIfAbsent(chatId, new ConcurrentHashMap<>()) == null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteChat(long chatId) {
        return linksByChatId.remove(chatId) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean chatExists(long chatId) {
        return linksByChatId.containsKey(chatId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TrackedLink> findByChatIdAndUrl(long chatId, String url) {
        Map<String, TrackedLink> links = linksByChatId.get(chatId);
        if (links == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(links.get(url));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TrackedLink save(long chatId, String url, List<String> tags, List<String> filters, Instant updatedAt) {
        TrackedLink trackedLink = new TrackedLink(
                idGenerator.getAndIncrement(),
                chatId,
                url,
                List.copyOf(tags),
                List.copyOf(filters),
                Instant.now(),
                updatedAt);
        linksByChatId.computeIfAbsent(chatId, key -> new ConcurrentHashMap<>()).put(url, trackedLink);
        return trackedLink;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteByChatIdAndUrl(long chatId, String url) {
        Map<String, TrackedLink> links = linksByChatId.get(chatId);
        if (links == null) {
            return false;
        }
        return links.remove(url) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TrackedLink> findAllByChatId(long chatId) {
        Map<String, TrackedLink> links = linksByChatId.get(chatId);
        if (links == null) {
            return List.of();
        }
        return List.copyOf(links.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TrackedLink> findAllByChatId(long chatId, int limit, int offset) {
        if (hasInvalidPaging(limit, offset)) {
            return List.of();
        }
        return findAllByChatId(chatId).stream().skip(offset).limit(limit).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Long> findAllChatIds() {
        return Set.copyOf(linksByChatId.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TrackedLink> findAllTrackedLinks(int limit, int offset) {
        if (hasInvalidPaging(limit, offset)) {
            return List.of();
        }
        return linksByChatId.values().stream()
                .flatMap(links -> links.values().stream())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateTrackedLink(TrackedLink trackedLink) {
        linksByChatId
                .computeIfAbsent(trackedLink.chatId(), key -> new ConcurrentHashMap<>())
                .put(trackedLink.url(), trackedLink);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> findTagsByChatIdAndUrl(long chatId, String url) {
        return findByChatIdAndUrl(chatId, url).map(TrackedLink::tags).orElse(List.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addTag(long chatId, String url, String tag) {
        Optional<TrackedLink> existing = findByChatIdAndUrl(chatId, url);
        if (existing.isEmpty()) {
            return false;
        }
        TrackedLink trackedLink = existing.get();
        if (trackedLink.tags().contains(tag)) {
            return false;
        }
        List<String> updatedTags = new java.util.ArrayList<>(trackedLink.tags());
        updatedTags.add(tag);
        updateTrackedLink(new TrackedLink(
                trackedLink.id(),
                trackedLink.chatId(),
                trackedLink.url(),
                List.copyOf(updatedTags),
                trackedLink.filters(),
                trackedLink.lastCheckedAt(),
                trackedLink.lastUpdatedAt()));
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateTag(long chatId, String url, String oldTag, String newTag) {
        Optional<TrackedLink> existing = findByChatIdAndUrl(chatId, url);
        if (existing.isEmpty()) {
            return false;
        }
        TrackedLink trackedLink = existing.get();
        if (!trackedLink.tags().contains(oldTag) || trackedLink.tags().contains(newTag)) {
            return false;
        }
        List<String> updatedTags = trackedLink.tags().stream()
                .map(tag -> tag.equals(oldTag) ? newTag : tag)
                .toList();
        updateTrackedLink(new TrackedLink(
                trackedLink.id(),
                trackedLink.chatId(),
                trackedLink.url(),
                List.copyOf(updatedTags),
                trackedLink.filters(),
                trackedLink.lastCheckedAt(),
                trackedLink.lastUpdatedAt()));
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteTag(long chatId, String url, String tag) {
        Optional<TrackedLink> existing = findByChatIdAndUrl(chatId, url);
        if (existing.isEmpty()) {
            return false;
        }
        TrackedLink trackedLink = existing.get();
        if (!trackedLink.tags().contains(tag)) {
            return false;
        }
        List<String> updatedTags = trackedLink.tags().stream()
                .filter(existingTag -> !existingTag.equals(tag))
                .toList();
        updateTrackedLink(new TrackedLink(
                trackedLink.id(),
                trackedLink.chatId(),
                trackedLink.url(),
                List.copyOf(updatedTags),
                trackedLink.filters(),
                trackedLink.lastCheckedAt(),
                trackedLink.lastUpdatedAt()));
        return true;
    }
}
