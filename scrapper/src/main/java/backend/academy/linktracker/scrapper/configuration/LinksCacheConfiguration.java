package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.LinksCacheProperties;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Конфигурация кэширования списка ссылок через Valkey.
 */
@Configuration
@EnableCaching
public class LinksCacheConfiguration {

    public static final String LINKS_LIST_CACHE = "links-list";

    /**
     * RedisCacheManager с JSON-сериализацией ответа /list.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.cache.links", name = "enabled", havingValue = "true", matchIfMissing = true)
    CacheManager redisLinksCacheManager(
            RedisConnectionFactory redisConnectionFactory, LinksCacheProperties properties) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(resolveTtl(properties))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(configuration)
                .build();
    }

    /**
     * Локальный кэш-менеджер для среды без Valkey.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.cache.links", name = "enabled", havingValue = "false")
    CacheManager localLinksCacheManager() {
        return new ConcurrentMapCacheManager(LINKS_LIST_CACHE);
    }

    private static Duration resolveTtl(LinksCacheProperties properties) {
        return properties.getTtl();
    }
}
