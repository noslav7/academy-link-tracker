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
import io.lettuce.core.cluster.RedisClusterClient;
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
            if (connectionFactory instanceof LettuceConnectionFactory lettuceConnectionFactory
                    && lettuceConnectionFactory.getNativeClient() instanceof RedisClusterClient) {
                RedisURI redisUri = firstClusterNodeRedisUri(lettuceConnectionFactory);
                RedisClient redisClient = RedisClient.create(redisUri);
                StatefulRedisConnection<String, byte[]> connection = redisClient.connect(STRING_BYTE_ARRAY_CODEC);
                LOGGER.info("Valkey cache enabled via cluster node {}:{}", redisUri.getHost(), redisUri.getPort());
                return new ValkeyLinksListCache(connection, properties.getTtl(), redisClient);
            }
            return new ValkeyLinksListCache(connectionFactory, properties.getTtl());
        }

        if (!(connectionFactory instanceof LettuceConnectionFactory lettuceConnectionFactory)) {
            LOGGER.warn("Client-side caching disabled: unsupported RedisConnectionFactory type");
            return new ValkeyLinksListCache(connectionFactory, properties.getTtl());
        }

        AbstractRedisClient nativeClient = lettuceConnectionFactory.getNativeClient();
        if (nativeClient instanceof RedisClient redisClient) {
            RedisURI redisUri = standaloneRedisUri(lettuceConnectionFactory);
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(STRING_BYTE_ARRAY_CODEC, redisUri);
            LOGGER.info("Client-side caching enabled via Valkey node {}:{}", redisUri.getHost(), redisUri.getPort());
            return new ValkeyClientSideLinksListCache(connection, properties.getTtl());
        }

        if (nativeClient instanceof RedisClusterClient) {
            RedisURI redisUri = firstClusterNodeRedisUri(lettuceConnectionFactory);
            RedisClient redisClient = RedisClient.create(redisUri);
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(STRING_BYTE_ARRAY_CODEC);
            LOGGER.info(
                    "Client-side caching enabled via Valkey cluster node {}:{}",
                    redisUri.getHost(),
                    redisUri.getPort());
            return new ValkeyClientSideLinksListCache(connection, properties.getTtl(), redisClient);
        }

        LOGGER.warn("Client-side caching disabled: unsupported Lettuce native client {}", nativeClient.getClass());
        return new ValkeyLinksListCache(connectionFactory, properties.getTtl());
    }

    /**
     * Режим без кэширования.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.cache.links", name = "enabled", havingValue = "false")
    LinksListCache noOpLinksCache() {
        return new NoOpLinksListCache();
    }

    private static RedisURI standaloneRedisUri(LettuceConnectionFactory lettuceConnectionFactory) {
        return RedisURI.create("redis://%s:%d/%d"
                .formatted(
                        lettuceConnectionFactory.getHostName(),
                        lettuceConnectionFactory.getPort(),
                        lettuceConnectionFactory.getDatabase()));
    }

    private static RedisURI firstClusterNodeRedisUri(LettuceConnectionFactory lettuceConnectionFactory) {
        var clusterConfiguration = lettuceConnectionFactory.getClusterConfiguration();
        if (clusterConfiguration == null
                || clusterConfiguration.getClusterNodes().isEmpty()) {
            throw new IllegalStateException("Redis cluster nodes must be configured for client-side cache");
        }
        var node = clusterConfiguration.getClusterNodes().iterator().next();
        return RedisURI.create("redis://%s:%d".formatted(node.getHost(), node.getPort()));
    }
}
