package backend.academy.linktracker.scrapper.service;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.configuration.LinksCacheConfiguration;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.ValkeyClusterTestcontainers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CachedLinksServiceValkeyIntegrationTest.TestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class CachedLinksServiceValkeyIntegrationTest {

    private static final Duration CACHE_TTL = Duration.ofSeconds(1);

    // Кластер поднимается один раз до создания Spring-контекста тестов.
    static {
        ValkeyClusterTestcontainers.start();
    }

    @Autowired
    private CachedLinksService cachedLinksService;

    @Autowired
    private LinkService linkService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * Сбрасывает моки и очищает кэш перед каждым тестовым сценарием.
     */
    @BeforeEach
    void setUp() {
        reset(linkService);
        Objects.requireNonNull(
                        cacheManager.getCache(LinksCacheConfiguration.LINKS_LIST_CACHE),
                        "Cache 'links-list' must be configured for the test context")
                .clear();
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void givenValkeyEnabledWhenListCalledTwiceThenSecondCallServedFromCache() {
        // Проверяет, что @Cacheable использует Valkey и не вызывает доменный сервис повторно.
        long chatId = 3001L;
        List<LinkResponse> links = List.of(new LinkResponse(1L, "https://example.com/a", List.of(), List.of()));
        when(linkService.list(chatId)).thenReturn(links);

        var firstResponse = cachedLinksService.list(chatId);
        awaitCachedEntry(chatId);

        reset(linkService);
        var secondResponse = cachedLinksService.list(chatId);

        assertEquals(firstResponse, secondResponse);
        verifyNoInteractions(linkService);
    }

    @Test
    void givenListResponseWhenCachedThenValkeyContainsExpectedSerializedPayload() {
        // Проверяет формат хранения: в Valkey записывается сериализованный ListLinksResponse по ожидаемому ключу.
        long chatId = 3005L;
        List<LinkResponse> links =
                List.of(new LinkResponse(77L, "https://example.com/serialized", List.of("tag"), List.of("filter")));
        when(linkService.list(chatId)).thenReturn(links);

        ListLinksResponse expected = cachedLinksService.list(chatId);
        awaitCachedEntry(chatId);
        byte[] rawPayload = cachedValue(chatId);
        Object cachedPayload = RedisSerializer.json().deserialize(rawPayload);

        assertAll(() -> assertNotNull(rawPayload), () -> assertEquals(expected, cachedPayload));
    }

    @Test
    void givenExpiredCacheWhenListCalledAgainThenValueIsReloadedFromDomainService() {
        // Проверяет TTL: после истечения записи ключ удаляется из Valkey и list снова вызывает доменный сервис.
        long chatId = 3004L;
        List<LinkResponse> links = List.of(new LinkResponse(100L, "https://example.com/ttl", List.of(), List.of()));
        when(linkService.list(chatId)).thenReturn(links);

        cachedLinksService.list(chatId);
        awaitCachedEntry(chatId);
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNull(cachedValue(chatId)));

        cachedLinksService.list(chatId);
        verify(linkService, times(2)).list(chatId);
    }

    @Test
    void givenCachedListWhenAddCalledThenCacheIsEvictedAndListReloaded() {
        // Проверяет, что @CacheEvict в add инвалидирует кэш и list снова идет в доменный сервис.
        long chatId = 3002L;
        AddLinkRequest request = new AddLinkRequest("https://example.com/new", List.of("tag"), List.of("filter"));
        LinkResponse added = new LinkResponse(2L, request.link(), request.tags(), request.filters());
        LinkResponse initialLink = new LinkResponse(1L, "https://example.com/old", List.of(), List.of());
        List<LinkResponse> beforeAdd = List.of(initialLink);
        List<LinkResponse> afterAdd = List.of(initialLink, added);
        when(linkService.list(chatId)).thenReturn(beforeAdd).thenReturn(afterAdd);
        when(linkService.add(chatId, request)).thenReturn(added);

        var listBeforeAdd = cachedLinksService.list(chatId);
        awaitCachedEntry(chatId);
        cachedLinksService.add(chatId, request);
        assertEquals(1, listBeforeAdd.size());
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNull(cachedValue(chatId)));
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var responseAfterAdd = cachedLinksService.list(chatId);
                    assertAll(
                            () -> assertEquals(afterAdd, responseAfterAdd.links()),
                            () -> assertEquals(afterAdd.size(), responseAfterAdd.size()));
                });
    }

    @Test
    void givenCachedListWhenRemoveCalledThenCacheIsEvictedAndListReloaded() {
        // Проверяет, что @CacheEvict в remove инвалидирует кэш и list повторно грузится из доменного сервиса.
        long chatId = 3003L;
        String removedLink = "https://example.com/remove";
        LinkResponse first = new LinkResponse(10L, removedLink, List.of(), List.of());
        LinkResponse second = new LinkResponse(11L, "https://example.com/keep", List.of(), List.of());
        List<LinkResponse> beforeRemove = List.of(first, second);
        List<LinkResponse> afterRemove = List.of(second);
        when(linkService.list(chatId)).thenReturn(beforeRemove).thenReturn(afterRemove);
        when(linkService.remove(chatId, removedLink)).thenReturn(first);

        var listBeforeRemove = cachedLinksService.list(chatId);
        awaitCachedEntry(chatId);
        cachedLinksService.remove(chatId, removedLink);
        assertEquals(2, listBeforeRemove.size());
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNull(cachedValue(chatId)));
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var responseAfterRemove = cachedLinksService.list(chatId);
                    assertAll(
                            () -> assertEquals(afterRemove, responseAfterRemove.links()),
                            () -> assertEquals(afterRemove.size(), responseAfterRemove.size()));
                });
    }

    /**
     * Ожидает появления записи в Valkey для конкретного chatId.
     *
     * <p>Используется из тестов, где запись в кэш происходит асинхронно относительно проверки.
     */
    private void awaitCachedEntry(long chatId) {
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNotNull(cachedValue(chatId)));
    }

    /**
     * Возвращает сырое значение кэша из Valkey по ключу `links-list::<chatId>`.
     *
     * <p>Нужно для проверок наличия/отсутствия данных и формата сериализации.
     */
    private byte[] cachedValue(long chatId) {
        byte[] cacheKey = (LinksCacheConfiguration.LINKS_LIST_CACHE + "::" + chatId).getBytes(StandardCharsets.UTF_8);
        try (var connection = redisConnectionFactory.getConnection()) {
            return connection.stringCommands().get(cacheKey);
        }
    }

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    /**
     * Минимальный Spring-контекст для тестирования `CachedLinksService`.
     *
     * <p>Содержит mock доменного сервиса, подключение к тестовому Valkey и CacheManager с коротким TTL.
     */
    static class TestConfig {

        /**
         * Регистрирует тестируемый сервис с кэш-аннотациями.
         */
        @Bean
        CachedLinksService cachedLinksService(LinkService linkService) {
            return new CachedLinksService(linkService);
        }

        /**
         * Возвращает mock `LinkService`, чтобы контролировать поведение доменного слоя.
         */
        @Bean
        @Primary
        LinkService linkService() {
            return Mockito.mock(LinkService.class);
        }

        /**
         * Создает подключение к Valkey для тестов.
         *
         * <p>Подключаемся к первой ноде кластера, что достаточно для сценариев работы кэша.
         */
        @Bean(destroyMethod = "destroy")
        RedisConnectionFactory redisConnectionFactory() {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                    ValkeyClusterTestcontainers.firstNodeHost(), ValkeyClusterTestcontainers.firstNodePort());
            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
            connectionFactory.afterPropertiesSet();
            return connectionFactory;
        }

        /**
         * Конфигурирует Redis CacheManager с коротким TTL и JSON-сериализацией.
         *
         * <p>Короткий TTL нужен для быстрого воспроизведения сценария автоматического истечения кэша.
         */
        @Bean
        CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
            RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(CACHE_TTL)
                    .serializeValuesWith(
                            RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()));
            return RedisCacheManager.builder(redisConnectionFactory)
                    .cacheDefaults(configuration)
                    .build();
        }
    }
}
