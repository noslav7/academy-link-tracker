package backend.academy.linktracker.scrapper;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        classes = {ScrapperApplication.class, TestcontainersConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.github.base-url=${wiremock.server.baseUrl}/github",
            "app.stackoverflow.base-url=${wiremock.server.baseUrl}/so",
            "app.bot.base-url=${wiremock.server.baseUrl}/bot",
            "app.scheduler.interval=365d"
        })
@EnableWireMock
@Tag("integration")
class ScrapperHttpContractTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpExternalApiStubs() {
        stubFor(get(urlMatching("/github/repos/.+/.+"))
                .willReturn(okJson("{\"updated_at\":\"2026-02-25T00:00:00Z\"}")));
    }

    @Test
    void givenRegisteredChatAndAddedLinkWhenListRequestedThenReturnsExactListResponse()
            throws IOException, InterruptedException {
        // Проверяет HTTP-контракт add/list: ответ списка полностью совпадает с эталонным JSON.
        long chatId = 1001L;
        String link = "https://github.com/user/repo-1001";

        assertEquals(200, postWithoutBody("/tg-chat/" + chatId).statusCode());
        HttpResponse<String> addResponse = postLink(chatId, link);
        assertEquals(200, addResponse.statusCode());

        HttpResponse<String> listResponse = getLinks(chatId);
        assertEquals(200, listResponse.statusCode());
        JsonNode expected = parseJson("""
                {
                  "links": [%s],
                  "size": 1
                }
                """.formatted(addResponse.body()));
        assertEquals(expected, parseJson(listResponse.body()));
    }

    @Test
    void givenAddedLinkWhenDeletedAndListRequestedThenReturnsEmptyListResponse()
            throws IOException, InterruptedException {
        // Проверяет HTTP-контракт delete/list: после удаления список должен быть строго пустым.
        long chatId = 1002L;
        String link = "https://github.com/user/repo-1002";

        assertEquals(200, postWithoutBody("/tg-chat/" + chatId).statusCode());
        assertEquals(200, postLink(chatId, link).statusCode());
        assertEquals(200, deleteLink(chatId, link).statusCode());

        HttpResponse<String> listResponse = getLinks(chatId);
        assertEquals(200, listResponse.statusCode());
        JsonNode expected = parseJson("""
                {
                  "links": [],
                  "size": 0
                }
                """);
        assertEquals(expected, parseJson(listResponse.body()));
    }

    @Test
    void givenMissingChatWhenDeleteRequestedThenReturnsNotFound() throws IOException, InterruptedException {
        // Проверяет контракт 404: удаление ссылки из несуществующего чата возвращает Not Found.
        long existingChatId = 1003L;
        long missingChatId = 1999L;
        String link = "https://github.com/user/repo-1003";

        assertEquals(200, postWithoutBody("/tg-chat/" + existingChatId).statusCode());
        assertEquals(200, postLink(existingChatId, link).statusCode());

        HttpResponse<String> deleteResponse = deleteLink(missingChatId, link);
        assertEquals(404, deleteResponse.statusCode());
    }

    @Test
    void givenMissingChatWhenDeleteRequestedThenExistingSubscriptionIsKept() throws IOException, InterruptedException {
        // Проверяет изоляцию состояния: неуспешное удаление в другом чате не затрагивает существующую подписку.
        long existingChatId = 1007L;
        long missingChatId = 1999L;
        String link = "https://github.com/user/repo-1007";

        assertEquals(200, postWithoutBody("/tg-chat/" + existingChatId).statusCode());
        HttpResponse<String> addResponse = postLink(existingChatId, link);
        assertEquals(200, addResponse.statusCode());

        assertEquals(404, deleteLink(missingChatId, link).statusCode());

        HttpResponse<String> listResponse = getLinks(existingChatId);
        assertEquals(200, listResponse.statusCode());
        JsonNode expected = parseJson("""
                {
                  "links": [%s],
                  "size": 1
                }
                """.formatted(addResponse.body()));
        assertEquals(expected, parseJson(listResponse.body()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("addLinkNotFoundCases")
    void givenChatIsNotAvailableWhenAddLinkRequestedThenReturnsNotFound(
            String ignoredCaseName, long chatId, AddLinkSetup setupAction) throws IOException, InterruptedException {
        // Проверяет контракт 404 add link для разных сценариев недоступного чата через параметризацию.
        setupAction.prepare(this, chatId);

        HttpResponse<String> addResponse = postLink(chatId, "https://github.com/user/repo-" + chatId);
        assertEquals(404, addResponse.statusCode());
        assertApiError(addResponse.body(), "404", "ChatNotFoundException", "Chat not found: " + chatId);
    }

    @Test
    void givenMissingChatWhenDeleteChatRequestedThenReturnsNotFound() throws IOException, InterruptedException {
        // Проверяет контракт 404: код и тело ошибки для удаления несуществующего чата.
        HttpResponse<String> deleteResponse = deleteWithoutBody("/tg-chat/1006");

        assertEquals(404, deleteResponse.statusCode());
        assertApiError(deleteResponse.body(), "404", "ChatNotFoundException", "Chat not found: 1006");
    }

    private JsonNode parseJson(String body) throws IOException {
        return objectMapper.readTree(body);
    }

    private static Stream<Arguments> addLinkNotFoundCases() {
        return Stream.of(
                Arguments.of("givenMissingChatWhenAddLinkRequestedThenReturnsNotFound", 2004L, (AddLinkSetup)
                        (test, chatId) -> {
                            // Готовим независимое состояние: в системе есть другой чат, но целевого чата нет.
                            assertEquals(
                                    200, test.postWithoutBody("/tg-chat/1004").statusCode());
                        }),
                Arguments.of("givenDeletedChatWhenAddLinkRequestedThenReturnsNotFound", 1005L, (AddLinkSetup)
                        (test, chatId) -> {
                            assertEquals(
                                    200,
                                    test.postWithoutBody("/tg-chat/" + chatId).statusCode());
                            assertEquals(
                                    200,
                                    test.deleteWithoutBody("/tg-chat/" + chatId).statusCode());
                        }));
    }

    private void assertApiError(String body, String expectedCode, String expectedException, String expectedMessage)
            throws IOException {
        JsonNode error = parseJson(body);
        assertAll(
                () -> assertEquals(expectedCode, error.path("code").asText()),
                () -> assertEquals(
                        expectedException, error.path("exceptionName").asText()),
                () -> assertEquals(
                        expectedMessage, error.path("exceptionMessage").asText()),
                () -> assertTrue(error.path("stacktrace").isArray()),
                () -> assertNotEquals(0, error.path("stacktrace").size()));
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
                .header("Tg-Chat-Id", String.valueOf(chatId))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getLinks(long chatId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/list"))
                .header("Tg-Chat-Id", String.valueOf(chatId))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteLink(long chatId, String link) throws IOException, InterruptedException {
        String body = """
                {
                  "link": "%s"
                }
                """.formatted(link);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/links"))
                .header("Tg-Chat-Id", String.valueOf(chatId))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutBody(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteWithoutBody(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(baseUrl() + path)).DELETE().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @FunctionalInterface
    private interface AddLinkSetup {
        void prepare(ScrapperHttpContractTest test, long chatId) throws IOException, InterruptedException;
    }
}
