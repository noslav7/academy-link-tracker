package backend.academy.linktracker.scrapper;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import backend.academy.linktracker.scrapper.configuration.LinksCacheConfiguration;
import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
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

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> VALKEY =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.1-alpine")).withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void registerValkeyProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

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
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNotNull(cachedValue(chatId)));

        byte[] cached = cachedValue(chatId);
        Object deserialized = RedisSerializer.json().deserialize(cached);

        assertAll(() -> assertNotNull(cached), () -> assertEquals(expected, deserialized));
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

        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNotNull(cachedValue(chatId)));

        assertEquals(200, postLink(chatId, secondLink).statusCode());
        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNull(cachedValue(chatId)));

        HttpResponse<String> rebuiltList = getLinks(chatId);
        assertEquals(200, rebuiltList.statusCode());
        ListLinksResponse afterAdd = objectMapper.readValue(rebuiltList.body(), ListLinksResponse.class);
        assertEquals(2, afterAdd.size());
    }

    private byte[] cachedValue(long chatId) {
        byte[] key = (LinksCacheConfiguration.LINKS_LIST_CACHE + "::" + chatId).getBytes(StandardCharsets.UTF_8);
        try (var connection = redisConnectionFactory.getConnection()) {
            return connection.stringCommands().get(key);
        }
    }

    private HttpResponse<String> postLink(long chatId, String link) throws IOException, InterruptedException {
        String body = """
                {
                  "link": "%s",
                  "tags": [],
                  "filters": []
                }
                """.formatted(link);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/links"))
                .header(TG_CHAT_ID_HEADER, String.valueOf(chatId))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getLinks(long chatId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/list"))
                .header(TG_CHAT_ID_HEADER, String.valueOf(chatId))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutBody(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
