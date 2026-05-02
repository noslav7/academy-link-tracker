package backend.academy.linktracker.scrapper.service.cache;

import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простой локальный кэш в памяти для режима без Valkey.
 */
public final class InMemoryLinksListCache implements LinksListCache, AutoCloseable {

    private final Map<Long, ListLinksResponse> cache = new ConcurrentHashMap<>();

    @Override
    public ListLinksResponse get(long chatId) {
        return cache.get(chatId);
    }

    @Override
    public void put(long chatId, ListLinksResponse response) {
        cache.put(chatId, response);
    }

    @Override
    public void evict(long chatId) {
        cache.remove(chatId);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void close() {
        clear();
    }
}
