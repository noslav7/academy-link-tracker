package backend.academy.linktracker.scrapper.service.cache;

import backend.academy.linktracker.scrapper.dto.ListLinksResponse;

/**
 * Реализация кэша-заглушки: ничего не хранит и всегда возвращает cache-miss.
 */
public final class NoOpLinksListCache implements LinksListCache {

    @Override
    public ListLinksResponse get(long chatId) {
        return null;
    }

    @Override
    public void put(long chatId, ListLinksResponse response) {
        // no-op
    }

    @Override
    public void evict(long chatId) {
        // no-op
    }

    @Override
    public void clear() {
        // no-op
    }
}
