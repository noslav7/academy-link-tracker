package backend.academy.linktracker.scrapper.service.cache;

import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Кэш списка ссылок с хранением значений в Valkey.
 */
public final class ValkeyLinksListCache implements LinksListCache {

    private static final RedisSerializer<Object> JSON_SERIALIZER = RedisSerializer.json();

    private final RedisConnectionFactory redisConnectionFactory;
    private final Duration ttl;

    public ValkeyLinksListCache(RedisConnectionFactory redisConnectionFactory, Duration ttl) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.ttl = ttl;
    }

    @Override
    public ListLinksResponse get(long chatId) {
        byte[] payload;
        try (var connection = redisConnectionFactory.getConnection()) {
            payload = connection.stringCommands().get(cacheKey(chatId));
        }
        return deserialize(payload);
    }

    @Override
    public void put(long chatId, ListLinksResponse response) {
        byte[] payload = JSON_SERIALIZER.serialize(response);
        if (payload == null) {
            return;
        }
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.stringCommands().setEx(cacheKey(chatId), ttl.toSeconds(), payload);
        }
    }

    @Override
    public void evict(long chatId) {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.keyCommands().del(cacheKey(chatId));
        }
    }

    @Override
    public void clear() {
        // Не используем FLUSHDB, чтобы не затрагивать данные, не относящиеся к кэшу /list.
    }

    private static byte[] cacheKey(long chatId) {
        return LinksListCacheKeys.byChatId(chatId).getBytes(StandardCharsets.UTF_8);
    }

    private static ListLinksResponse deserialize(byte[] payload) {
        if (payload == null) {
            return null;
        }
        Object value = JSON_SERIALIZER.deserialize(payload);
        if (value instanceof ListLinksResponse response) {
            return response;
        }
        return null;
    }
}
