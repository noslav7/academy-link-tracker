package backend.academy.linktracker.scrapper.service.cache;

import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Кэш списка ссылок с хранением значений в Valkey.
 */
public final class ValkeyLinksListCache implements LinksListCache, AutoCloseable {

    private static final RedisSerializer<Object> JSON_SERIALIZER = RedisSerializer.json();

    private final RedisConnectionFactory redisConnectionFactory;
    private final StatefulRedisConnection<String, byte[]> connection;
    private final AbstractRedisClient ownedClient;
    private final Duration ttl;

    public ValkeyLinksListCache(RedisConnectionFactory redisConnectionFactory, Duration ttl) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.connection = null;
        this.ownedClient = null;
        this.ttl = ttl;
    }

    public ValkeyLinksListCache(
            StatefulRedisConnection<String, byte[]> connection, Duration ttl, AbstractRedisClient ownedClient) {
        this.redisConnectionFactory = null;
        this.connection = connection;
        this.ownedClient = ownedClient;
        this.ttl = ttl;
    }

    @Override
    public ListLinksResponse get(long chatId) {
        if (connection != null) {
            return deserialize(connection.sync().get(LinksListCacheKeys.byChatId(chatId)));
        }

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
        if (connection != null) {
            connection.sync().setex(LinksListCacheKeys.byChatId(chatId), ttl.toSeconds(), payload);
            return;
        }

        try (var connection = redisConnectionFactory.getConnection()) {
            connection.stringCommands().setEx(cacheKey(chatId), ttl.toSeconds(), payload);
        }
    }

    @Override
    public void evict(long chatId) {
        if (connection != null) {
            connection.sync().del(LinksListCacheKeys.byChatId(chatId));
            return;
        }

        try (var connection = redisConnectionFactory.getConnection()) {
            connection.keyCommands().del(cacheKey(chatId));
        }
    }

    @Override
    public void clear() {
        // Не используем FLUSHDB, чтобы не затрагивать данные, не относящиеся к кэшу /list.
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (ownedClient != null) {
            ownedClient.shutdown();
        }
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
