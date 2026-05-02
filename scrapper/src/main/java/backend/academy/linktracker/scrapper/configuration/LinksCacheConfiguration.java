package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.LinksCacheProperties;
import backend.academy.linktracker.scrapper.properties.LinksCacheProperties.CacheMode;
import backend.academy.linktracker.scrapper.service.cache.LinksListCache;
import backend.academy.linktracker.scrapper.service.cache.LinksListCacheKeys;
import backend.academy.linktracker.scrapper.service.cache.NoOpLinksListCache;
import backend.academy.linktracker.scrapper.service.cache.ValkeyClientSideLinksListCache;
import backend.academy.linktracker.scrapper.service.cache.ValkeyLinksListCache;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Конфигурация кэширования списка ссылок через Valkey.
 */
@Configuration
public class LinksCacheConfiguration {

    /**
     * Оставлено для обратной совместимости с существующими интеграциями и тестами.
     */
    public static final String LINKS_LIST_CACHE = LinksListCacheKeys.CACHE_NAME;

    private static final Logger LOGGER = LoggerFactory.getLogger(LinksCacheConfiguration.class);
    private static final RedisCodec<String, byte[]> STRING_BYTE_ARRAY_CODEC =
            RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);

    /**
     * Кэш через Valkey с поддержкой server-assisted client-side caching.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.cache.links", name = "enabled", havingValue = "true", matchIfMissing = true)
    LinksListCache linksListCache(RedisConnectionFactory connectionFactory, LinksCacheProperties properties) {
        if (properties.getMode() == CacheMode.NONE) {
            return new NoOpLinksListCache();
        }
        if (properties.getMode() == CacheMode.VALKEY) {
            return new ValkeyLinksListCache(connectionFactory, properties.getTtl());
        }

        if (!(connectionFactory instanceof LettuceConnectionFactory lettuceConnectionFactory)) {
            LOGGER.warn("Client-side caching disabled: unsupported RedisConnectionFactory type");
            return new ValkeyLinksListCache(connectionFactory, properties.getTtl());
        }

        AbstractRedisClient nativeClient = lettuceConnectionFactory.getNativeClient();
        if (!(nativeClient instanceof RedisClient redisClient)) {
            LOGGER.warn("Client-side caching disabled: current Valkey topology does not support Lettuce CacheFrontend");
            return new ValkeyLinksListCache(connectionFactory, properties.getTtl());
        }

        RedisURI redisUri = RedisURI.create("redis://%s:%d/%d"
                .formatted(
                        lettuceConnectionFactory.getHostName(),
                        lettuceConnectionFactory.getPort(),
                        lettuceConnectionFactory.getDatabase()));
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(STRING_BYTE_ARRAY_CODEC, redisUri);
        return new ValkeyClientSideLinksListCache(connection, properties.getTtl());
    }

    /**
     * Режим без кэширования.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.cache.links", name = "enabled", havingValue = "false")
    LinksListCache noOpLinksCache() {
        return new NoOpLinksListCache();
    }
}
