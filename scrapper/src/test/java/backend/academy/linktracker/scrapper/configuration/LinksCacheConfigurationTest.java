package backend.academy.linktracker.scrapper.configuration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import backend.academy.linktracker.scrapper.properties.LinksCacheProperties;
import backend.academy.linktracker.scrapper.properties.LinksCacheProperties.CacheMode;
import backend.academy.linktracker.scrapper.service.cache.LinksListCache;
import backend.academy.linktracker.scrapper.service.cache.NoOpLinksListCache;
import backend.academy.linktracker.scrapper.service.cache.ValkeyLinksListCache;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest(name = "givenMode{0}WhenCreatingCacheBeanThenExpectedImplementationIsReturned")
    @MethodSource("cacheModeToImplementation")
    void givenCacheModeWhenCreatingCacheBeanThenExpectedImplementationIsReturned(
            CacheMode mode, Class<? extends LinksListCache> expectedImplementation) {
        // Проверяет контракт выбора реализации по mode (в т.ч. fallback CLIENT_SIDE без Lettuce).
        LinksCacheConfiguration configuration = new LinksCacheConfiguration();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        LinksCacheProperties properties = new LinksCacheProperties();
        properties.setTtl(Duration.ofSeconds(5));
        properties.setMode(mode);

        LinksListCache cache = configuration.linksListCache(connectionFactory, properties);

        assertInstanceOf(expectedImplementation, cache);
    }

    private static Stream<Arguments> cacheModeToImplementation() {
        return Stream.of(
                Arguments.of(CacheMode.NONE, NoOpLinksListCache.class),
                Arguments.of(CacheMode.VALKEY, ValkeyLinksListCache.class),
                Arguments.of(CacheMode.CLIENT_SIDE, ValkeyLinksListCache.class));
    }
}
