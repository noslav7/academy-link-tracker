package backend.academy.linktracker.scrapper;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.service.cache.LinksListCacheKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        classes = {ScrapperApplication.class, TestcontainersConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.cache.links.enabled=true",
            "app.cache.links.ttl=PT1M",
            "app.github.base-url=${wiremock.server.baseUrl}/github",
            "app.stackoverflow.base-url=${wiremock.server.baseUrl}/so",
            "app.bot.base-url=${wiremock.server.baseUrl}/bot",
            "app.scheduler.interval=365d"
        })
@EnableWireMock
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class CachedLinksHttpValkeyIntegrationTest {

    private static final String TG_CHAT_ID_HEADER = "Tg-Chat-Id";
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long CACHE_AWAIT_TIMEOUT_SECONDS = 10;
    private static final long CACHE_POLL_INTERVAL_MILLIS = 50;

    // Инициализируем общий 3-нодовый кластер до поднятия Spring-контекста теста.
    static {
        ValkeyClusterTestcontainers.start();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Прокидывает в Spring теста параметры подключения к Valkey.
     *
     * <p>Для текущих сценариев достаточно подключения к первой ноде (master), при этом
     * инфраструктура в фоне остается кластерной, как требует задание.
     */
    @DynamicPropertySource
    static void registerValkeyProperties(DynamicPropertyRegistry registry) {
        ValkeyClusterTestcontainers.registerSpringStandaloneProperties(registry);
    }

    /**
     * Подготавливает окружение перед каждым тестом:
     * настраивает мок GitHub-ответа и очищает Valkey.
     */
    @BeforeEach
    void setUp() {
        stubFor(get(urlMatching("/github/repos/.+/.+"))
                .willReturn(okJson("{\"updated_at\":\"2026-02-25T00:00:00Z\"}")));
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void givenListRequestedViaHttpWhenResponseCachedThenValkeyContainsExpectedSerializedPayload()
            throws IOException, InterruptedException {
        // Проверяет, что HTTP /list сохраняет в Valkey сериализованный ListLinksResponse по ожидаемому ключу.
        long chatId = 4101L;
        String firstLink = "https://github.com/user/repo-4101";

        assertEquals(200, postWithoutBody("/tg-chat/" + chatId).statusCode());
        assertEquals(200, postLink(chatId, firstLink).statusCode());
        HttpResponse<String> firstListResponse = getLinks(chatId);
        assertEquals(200, firstListResponse.statusCode());

        ListLinksResponse expected = objectMapper.readValue(firstListResponse.body(), ListLinksResponse.class);
        waitForCache(chatId);

        byte[] cached = cachedValue(chatId);
        Object deserialized = RedisSerializer.json().deserialize(cached);
        ListLinksResponse actualCachedPayload = assertInstanceOf(ListLinksResponse.class, deserialized);

        assertAll(() -> assertNotNull(cached), () -> assertEquals(expected, actualCachedPayload));
    }

    @Test
    void givenCacheCreatedViaHttpWhenLinksChangedThenCacheEvictedAndListRebuilt()
            throws IOException, InterruptedException {
        // Проверяет инвалидацию через API: после add кэш удаляется, а следующий /list формирует новое состояние.
        long chatId = 4102L;
        String firstLink = "https://github.com/user/repo-4102-a";
        String secondLink = "https://github.com/user/repo-4102-b";

        assertEquals(200, postWithoutBody("/tg-chat/" + chatId).statusCode());
        assertEquals(200, postLink(chatId, firstLink).statusCode());
        HttpResponse<String> cachedList = getLinks(chatId);
        assertEquals(200, cachedList.statusCode());
        ListLinksResponse beforeAdd = objectMapper.readValue(cachedList.body(), ListLinksResponse.class);
        assertEquals(1, beforeAdd.size());

        waitForCache(chatId);

        assertEquals(200, postLink(chatId, secondLink).statusCode());
        waitForCacheEviction(chatId);

        HttpResponse<String> rebuiltList = getLinks(chatId);
        assertEquals(200, rebuiltList.statusCode());
        ListLinksResponse afterAdd = objectMapper.readValue(rebuiltList.body(), ListLinksResponse.class);
        assertAll(
                () -> assertEquals(2, afterAdd.size()),
                () -> assertEquals(
                        List.of(firstLink, secondLink),
                        afterAdd.links().stream().map(LinkResponse::url).toList()));
    }

    @Test
    void givenCacheCreatedViaHttpWhenLinkRemovedThenCacheEvictedAndListRebuilt()
            throws IOException, InterruptedException {
        // Проверяет инвалидацию через API: после delete кэш удаляется, а следующий /list возвращает обновленный список.
        long chatId = 4103L;
        String firstLink = "https://github.com/user/repo-4103-a";
        String secondLink = "https://github.com/user/repo-4103-b";

        assertEquals(200, postWithoutBody("/tg-chat/" + chatId).statusCode());
        assertEquals(200, postLink(chatId, firstLink).statusCode());
        assertEquals(200, postLink(chatId, secondLink).statusCode());
        HttpResponse<String> cachedList = getLinks(chatId);
        assertEquals(200, cachedList.statusCode());
        ListLinksResponse beforeDelete = objectMapper.readValue(cachedList.body(), ListLinksResponse.class);
        assertEquals(2, beforeDelete.size());

        waitForCache(chatId);

        assertEquals(200, deleteLink(chatId, secondLink).statusCode());
        waitForCacheEviction(chatId);

        HttpResponse<String> rebuiltList = getLinks(chatId);
        assertEquals(200, rebuiltList.statusCode());
        ListLinksResponse afterDelete = objectMapper.readValue(rebuiltList.body(), ListLinksResponse.class);
        assertAll(
                () -> assertEquals(1, afterDelete.size()),
                () -> assertEquals(
                        List.of(firstLink),
                        afterDelete.links().stream().map(LinkResponse::url).toList()));
    }

    /**
     * Возвращает сырое значение кэша по chatId из `LinksListCacheKeys.byChatId(chatId)`.
     *
     * <p>Нужно для явной проверки формата хранения данных в Valkey.
     */
    private byte[] cachedValue(long chatId) {
        byte[] key = LinksListCacheKeys.byChatId(chatId).getBytes(StandardCharsets.UTF_8);
        try (var connection = redisConnectionFactory.getConnection()) {
            return connection.stringCommands().get(key);
        }
    }

    private void waitForCache(long chatId) {
        await().atMost(CACHE_AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(CACHE_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNotNull(cachedValue(chatId)));
    }

    private void waitForCacheEviction(long chatId) {
        await().atMost(CACHE_AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(CACHE_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNull(cachedValue(chatId)));
    }

    /**
     * Отправляет HTTP-запрос на добавление ссылки пользователю.
     *
     * <p>Используется в сценариях, где после изменения ссылок кэш должен инвалидироваться.
     */
    private HttpResponse<String> postLink(long chatId, String link) throws IOException, InterruptedException {
        String body = """
                {
                  "link": "%s",
                  "tags": [],
                  "filters": []
                }
                """.formatted(link);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/links"))
                .timeout(HTTP_REQUEST_TIMEOUT)
                .header(TG_CHAT_ID_HEADER, String.valueOf(chatId))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Отправляет HTTP GET `/list` и получает список ссылок для пользователя.
     *
     * <p>Это основной endpoint, для которого проверяется кэширование.
     */
    private HttpResponse<String> getLinks(long chatId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/list"))
                .timeout(HTTP_REQUEST_TIMEOUT)
                .header(TG_CHAT_ID_HEADER, String.valueOf(chatId))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Отправляет HTTP DELETE для удаления ссылки у пользователя.
     *
     * <p>Используется для проверки, что удаление инвалидирует ранее построенный кэш.
     */
    private HttpResponse<String> deleteLink(long chatId, String link) throws IOException, InterruptedException {
        String body = """
                {
                  "link": "%s"
                }
                """.formatted(link);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/links"))
                .timeout(HTTP_REQUEST_TIMEOUT)
                .header(TG_CHAT_ID_HEADER, String.valueOf(chatId))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Вспомогательный POST без тела для регистрации чата в тестовых сценариях.
     */
    private HttpResponse<String> postWithoutBody(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(HTTP_REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Возвращает базовый URL случайного порта тестового Spring Boot приложения.
     */
    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
