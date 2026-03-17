package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.ChatEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.TrackedLinkEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.TrackedLinkFilterEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.TrackedLinkTagEntity;
import backend.academy.linktracker.scrapper.repository.orm.jpa.ChatJpaRepository;
import backend.academy.linktracker.scrapper.repository.orm.jpa.TrackedLinkJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORM-реализация репозитория отслеживаемых ссылок.
 */
@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
public class OrmTrackedLinkRepository implements TrackedLinkRepository {

    private final ChatJpaRepository chatJpaRepository;
    private final TrackedLinkJpaRepository trackedLinkJpaRepository;

    public OrmTrackedLinkRepository(
            ChatJpaRepository chatJpaRepository, TrackedLinkJpaRepository trackedLinkJpaRepository) {
        this.chatJpaRepository = chatJpaRepository;
        this.trackedLinkJpaRepository = trackedLinkJpaRepository;
    }

    @Override
    @Transactional
    public boolean registerChat(long chatId) {
        if (chatJpaRepository.existsById(chatId)) {
            return false;
        }
        chatJpaRepository.save(new ChatEntity(chatId));
        return true;
    }

    @Override
    @Transactional
    public boolean deleteChat(long chatId) {
        if (!chatJpaRepository.existsById(chatId)) {
            return false;
        }
        chatJpaRepository.deleteById(chatId);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean chatExists(long chatId) {
        return chatJpaRepository.existsById(chatId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TrackedLink> findByChatIdAndUrl(long chatId, String url) {
        return trackedLinkJpaRepository.findByChatChatIdAndUrl(chatId, url).map(this::toDomain);
    }

    @Override
    @Transactional
    public TrackedLink save(long chatId, String url, List<String> tags, List<String> filters, Instant updatedAt) {
        ChatEntity chat = chatJpaRepository.getReferenceById(chatId);
        Instant now = Instant.now();

        TrackedLinkEntity trackedLink = new TrackedLinkEntity(chat, url, now, updatedAt);
        for (String tag : tags) {
            trackedLink.getTags().add(new TrackedLinkTagEntity(trackedLink, tag));
        }
        for (String filter : filters) {
            trackedLink.getFilters().add(new TrackedLinkFilterEntity(trackedLink, filter));
        }

        TrackedLinkEntity saved = trackedLinkJpaRepository.save(trackedLink);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public boolean deleteByChatIdAndUrl(long chatId, String url) {
        Optional<TrackedLinkEntity> existing = trackedLinkJpaRepository.findByChatChatIdAndUrl(chatId, url);
        if (existing.isEmpty()) {
            return false;
        }
        trackedLinkJpaRepository.delete(existing.get());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedLink> findAllByChatId(long chatId) {
        return findAllByChatId(chatId, Integer.MAX_VALUE, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedLink> findAllByChatId(long chatId, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            return List.of();
        }
        int page = offset / limit;
        int intraOffset = offset % limit;
        List<TrackedLink> links =
                trackedLinkJpaRepository.findByChatChatIdOrderById(chatId, PageRequest.of(page, limit)).stream()
                        .map(this::toDomain)
                        .toList();
        if (intraOffset == 0) {
            return links;
        }
        if (intraOffset >= links.size()) {
            return List.of();
        }
        return links.subList(intraOffset, links.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findAllChatIds() {
        return Set.copyOf(chatJpaRepository.findAllChatIds());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackedLink> findAllTrackedLinks(int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            return List.of();
        }
        int page = offset / limit;
        int intraOffset = offset % limit;
        List<TrackedLink> links = trackedLinkJpaRepository.findAllByOrderById(PageRequest.of(page, limit)).stream()
                .map(this::toDomain)
                .toList();
        if (intraOffset == 0) {
            return links;
        }
        if (intraOffset >= links.size()) {
            return List.of();
        }
        return links.subList(intraOffset, links.size());
    }

    @Override
    @Transactional
    public void updateTrackedLink(TrackedLink trackedLink) {
        trackedLinkJpaRepository.findById(trackedLink.id()).ifPresent(entity -> {
            entity.setLastCheckedAt(trackedLink.lastCheckedAt());
            entity.setLastUpdatedAt(trackedLink.lastUpdatedAt());
        });
    }

    private TrackedLink toDomain(TrackedLinkEntity entity) {
        return new TrackedLink(
                entity.getId(),
                entity.getChat().getChatId(),
                entity.getUrl(),
                entity.getTags().stream().map(TrackedLinkTagEntity::getTag).toList(),
                entity.getFilters().stream()
                        .map(TrackedLinkFilterEntity::getFilterValue)
                        .toList(),
                entity.getLastCheckedAt(),
                entity.getLastUpdatedAt());
    }
}
