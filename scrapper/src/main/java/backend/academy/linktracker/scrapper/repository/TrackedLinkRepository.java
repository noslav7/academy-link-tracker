package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TrackedLinkRepository {
    boolean registerChat(long chatId);

    boolean deleteChat(long chatId);

    boolean chatExists(long chatId);

    Optional<TrackedLink> findByChatIdAndUrl(long chatId, String url);

    TrackedLink save(long chatId, String url, List<String> tags, List<String> filters, java.time.Instant updatedAt);

    boolean deleteByChatIdAndUrl(long chatId, String url);

    List<TrackedLink> findAllByChatId(long chatId);

    Set<Long> findAllChatIds();

    void updateTrackedLink(TrackedLink trackedLink);
}
