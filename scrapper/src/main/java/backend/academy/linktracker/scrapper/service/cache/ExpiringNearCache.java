package backend.academy.linktracker.scrapper.service.cache;

import io.lettuce.core.support.caching.CacheAccessor;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Локальный near-cache c TTL для client-side caching.
 */
final class ExpiringNearCache implements CacheAccessor<String, byte[]> {

    /**
     * Время жизни записи в миллисекундах.
     */
    private final long ttlMs;
    /**
     * Хранилище записей near-cache.
     */
    private final Map<String, Entry> values;

    /**
     * Создает near-cache с TTL.
     *
     * @param ttl время жизни записи
     * @implNote Нужно, чтобы near-cache автоматически отбрасывал устаревшие данные без обращения к Valkey.
     */
    ExpiringNearCache(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttlMs = ttl.toMillis();
        this.values = new ConcurrentHashMap<>();
    }

    /**
     * Возвращает запись из near-cache, если она еще не истекла.
     *
     * @param key ключ записи
     * @return сериализованное значение или {@code null}
     * @implNote Нужно, чтобы не отдавать устаревшие данные при локальном чтении.
     */
    @Override
    public byte[] get(String key) {
        Entry entry = values.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAtEpochMs < System.currentTimeMillis()) {
            values.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * Сохраняет запись в near-cache с вычисленным временем истечения.
     *
     * @param key ключ записи
     * @param value сериализованное значение
     * @implNote Нужно, чтобы локальный кэш оставался синхронизированным по политике TTL с удаленным кэшем.
     */
    @Override
    public void put(String key, byte[] value) {
        values.put(key, new Entry(value, System.currentTimeMillis() + ttlMs));
    }

    /**
     * Удаляет запись из near-cache по ключу.
     *
     * @param key ключ записи
     * @implNote Нужно, чтобы принудительно сбросить локальное значение после мутации в Valkey.
     */
    @Override
    public void evict(String key) {
        values.remove(key);
    }

    /**
     * Полностью очищает near-cache.
     *
     * @implNote Нужно, чтобы после реконнекта не использовать потенциально несогласованные локальные данные.
     */
    void clear() {
        values.clear();
    }

    /**
     * Значение near-cache и момент его истечения.
     *
     * @param value сериализованное значение
     * @param expireAtEpochMs время истечения в epoch milliseconds
     */
    private record Entry(byte[] value, long expireAtEpochMs) {}
}
