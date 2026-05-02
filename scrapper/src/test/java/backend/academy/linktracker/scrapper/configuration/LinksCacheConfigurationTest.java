package backend.academy.linktracker.scrapper.configuration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import backend.academy.linktracker.scrapper.properties.LinksCacheProperties;
import backend.academy.linktracker.scrapper.properties.LinksCacheProperties.CacheMode;
import backend.academy.linktracker.scrapper.service.cache.LinksListCache;
import backend.academy.linktracker.scrapper.service.cache.NoOpLinksListCache;
import backend.academy.linktracker.scrapper.service.cache.ValkeyLinksListCache;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Tag("unit")
@Tag("sanity")
class LinksCacheConfigurationTest {

    @Test
    void givenDisabledCacheWhenCreatingLocalBeanThenNoOpCacheIsReturned() {
        // Проверяет путь без кэширования: при LINKS_CACHE_ENABLED=false записи не кэшируются.
        LinksCacheConfiguration configuration = new LinksCacheConfiguration();

        LinksListCache cache = configuration.noOpLinksCache();

        assertInstanceOf(NoOpLinksListCache.class, cache);
    }

    @Test
    void givenNoneModeWhenCreatingCacheBeanThenNoOpCacheIsReturned() {
        // Проверяет mode=NONE: кэш отключается даже при включенном флаге app.cache.links.enabled.
        LinksCacheConfiguration configuration = new LinksCacheConfiguration();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        LinksCacheProperties properties = new LinksCacheProperties();
        properties.setTtl(Duration.ofSeconds(5));
        properties.setMode(CacheMode.NONE);

        LinksListCache cache = configuration.linksListCache(connectionFactory, properties);

        assertInstanceOf(NoOpLinksListCache.class, cache);
    }

    @Test
    void givenValkeyModeWhenCreatingCacheBeanThenValkeyCacheIsReturned() {
        // Проверяет mode=VALKEY: используется серверный Valkey-кэш без client-side логики.
        LinksCacheConfiguration configuration = new LinksCacheConfiguration();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        LinksCacheProperties properties = new LinksCacheProperties();
        properties.setTtl(Duration.ofSeconds(5));
        properties.setMode(CacheMode.VALKEY);

        LinksListCache cache = configuration.linksListCache(connectionFactory, properties);

        assertInstanceOf(ValkeyLinksListCache.class, cache);
    }

    @Test
    void givenClientSideModeAndUnsupportedFactoryWhenCreatingCacheBeanThenFallbackToValkeyCache() {
        // Проверяет деградацию mode=CLIENT_SIDE: без LettuceConnectionFactory включается fallback на
        // ValkeyLinksListCache.
        LinksCacheConfiguration configuration = new LinksCacheConfiguration();
        RedisConnectionFactory unsupportedConnectionFactory = mock(RedisConnectionFactory.class);
        LinksCacheProperties properties = new LinksCacheProperties();
        properties.setTtl(Duration.ofSeconds(5));
        properties.setMode(CacheMode.CLIENT_SIDE);

        LinksListCache cache = configuration.linksListCache(unsupportedConnectionFactory, properties);

        assertInstanceOf(ValkeyLinksListCache.class, cache);
    }
}
