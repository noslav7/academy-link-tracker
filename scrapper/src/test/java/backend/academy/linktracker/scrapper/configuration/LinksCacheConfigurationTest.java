package backend.academy.linktracker.scrapper.configuration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import backend.academy.linktracker.scrapper.properties.LinksCacheProperties;
import backend.academy.linktracker.scrapper.service.cache.LinksListCache;
import backend.academy.linktracker.scrapper.service.cache.NoOpLinksListCache;
import backend.academy.linktracker.scrapper.service.cache.ValkeyLinksListCache;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Tag("unit")
class LinksCacheConfigurationTest {

    @Test
    void givenDisabledCacheWhenCreatingLocalBeanThenNoOpCacheIsReturned() {
        // Проверяет путь без кэширования: при LINKS_CACHE_ENABLED=false записи не кэшируются.
        LinksCacheConfiguration configuration = new LinksCacheConfiguration();

        LinksListCache cache = configuration.noOpLinksCache();

        assertInstanceOf(NoOpLinksListCache.class, cache);
    }

    @Test
    void givenUnsupportedConnectionFactoryWhenCreatingValkeyBeanThenInMemoryCacheIsReturned() {
        // Проверяет безопасную деградацию: без LettuceConnectionFactory включается локальный кэш.
        LinksCacheConfiguration configuration = new LinksCacheConfiguration();
        RedisConnectionFactory unsupportedConnectionFactory = mock(RedisConnectionFactory.class);
        LinksCacheProperties properties = new LinksCacheProperties();
        properties.setTtl(Duration.ofSeconds(5));
        properties.setMode(LinksCacheProperties.CacheMode.CLIENT_SIDE);

        LinksListCache cache = configuration.linksListCache(unsupportedConnectionFactory, properties);

        assertInstanceOf(ValkeyLinksListCache.class, cache);
    }
}
