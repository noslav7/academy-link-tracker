package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * In-memory реализация репозитория отслеживаемых ссылок.
 */
@Repository
public class InMemoryTrackedLinkRepository implements TrackedLinkRepository {

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
    public Set<Long> findAllChatIds() {
        return Set.copyOf(linksByChatId.keySet());
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
}
