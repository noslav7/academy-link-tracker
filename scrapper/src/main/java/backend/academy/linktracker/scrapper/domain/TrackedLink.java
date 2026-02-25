package backend.academy.linktracker.scrapper.domain;

import java.time.Instant;
import java.util.List;

public record TrackedLink(
        long id,
        long chatId,
        String url,
        List<String> tags,
        List<String> filters,
        Instant lastCheckedAt,
        Instant lastUpdatedAt) {

    public TrackedLink withLastCheck(Instant checkedAt, Instant updatedAt) {
        return new TrackedLink(id, chatId, url, tags, filters, checkedAt, updatedAt);
    }
}
