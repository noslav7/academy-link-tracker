package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.client.dto.AddLinkRequest;
import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.bot.client.dto.ListLinksResponse;
import backend.academy.linktracker.bot.client.dto.RemoveLinkRequest;
import backend.academy.linktracker.bot.metrics.BotMetricsRecorder;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP-реализация клиента взаимодействия с scrapper API.
 */
public class RestScrapperClient implements ScrapperClient {
    /**
     * Имя scope для метрик синхронных вызовов scrapper API.
     * Нужен как стабильный идентификатор в метриках bot-клиента.
     */
    private static final String SCOPE_SCRAPPER_SYNC_API = "scrapper_sync_api";
    /**
     * HTTP-статусы, означающие «ссылка уже отслеживается».
     * Нужны для явного маппинга бизнес-ответа в доменное исключение.
     */
    private static final Set<Integer> ALREADY_TRACKED_HTTP_STATUSES = Set.of(409);
    /**
     * HTTP-статусы, означающие «ссылка не найдена».
     * Нужны для единообразного маппинга remove-операции.
     */
    private static final Set<Integer> LINK_NOT_FOUND_HTTP_STATUSES = Set.of(404);

    /**
     * REST-клиент вызовов scrapper API.
     */
    private final RestClient restClient;

    /**
     * Recorder клиентских метрик bot.
     */
    private final BotMetricsRecorder metricsRecorder;

    /**
     * Создает HTTP-клиент scrapper-сервиса.
     *
     * @param scrapperRestClient REST-клиент scrapper
     */
    public RestScrapperClient(RestClient scrapperRestClient) {
        this(scrapperRestClient, BotMetricsRecorder.noop());
    }

    public RestScrapperClient(RestClient scrapperRestClient, BotMetricsRecorder metricsRecorder) {
        this.restClient = scrapperRestClient;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Регистрирует Telegram-чат в scrapper по HTTP.
     *
     * @param chatId идентификатор чата
     * @implNote Нужен для инициализации контекста чата до выполнения пользовательских команд бота.
     */
    @Override
    public void registerChat(long chatId) {
        metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "registerChat", () -> {
            try {
                restClient
                        .post()
                        .uri("/tg-chat/{id}", chatId)
                        .retrieve()
                        .onStatus(
                                statusCode -> ALREADY_TRACKED_HTTP_STATUSES.contains(statusCode.value()),
                                (request, response) -> {})
                        .toBodilessEntity();
            } catch (RestClientException ex) {
                throw new ScrapperServiceUnavailableException("Failed to register chat in scrapper", ex);
            }
        });
    }

    /**
     * Добавляет ссылку в трекинг через HTTP-вызов scrapper.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для отслеживания
     * @param tags теги подписки
     * @param filters фильтры подписки
     * @return созданная подписка
     * @implNote Нужен как синхронный шаг сценария `/track`, чтобы сразу вернуть пользователю результат операции.
     */
    @Override
    public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
        String normalizedLink = ScrapperClientInputSanitizer.normalizeLink(link);
        List<String> normalizedTags = ScrapperClientInputSanitizer.normalizeTags(tags);
        List<String> normalizedFilters = ScrapperClientInputSanitizer.normalizeFilters(filters);
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "addLink", () -> {
            try {
                return restClient
                        .post()
                        .uri("/links")
                        .header("Tg-Chat-Id", String.valueOf(chatId))
                        .body(new AddLinkRequest(normalizedLink, normalizedTags, normalizedFilters))
                        .retrieve()
                        .onStatus(
                                statusCode -> ALREADY_TRACKED_HTTP_STATUSES.contains(statusCode.value()),
                                (request, response) -> {
                                    throw new LinkAlreadyTrackedException();
                                })
                        .body(LinkResponse.class);
            } catch (LinkAlreadyTrackedException ex) {
                throw ex;
            } catch (RestClientException ex) {
                throw ScrapperClientErrorMapper.mapRestFailure("Failed to add link in scrapper", ex);
            }
        });
    }

    /**
     * Удаляет ссылку из трекинга через HTTP-вызов scrapper.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для удаления
     * @return удаленная подписка
     * @implNote Нужен для команды `/untrack` с доменным различением «нет такой ссылки» и транспортных сбоев.
     */
    @Override
    public LinkResponse removeLink(long chatId, String link) {
        String normalizedLink = ScrapperClientInputSanitizer.normalizeLink(link);
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "removeLink", () -> {
            try {
                return restClient
                        .method(HttpMethod.DELETE)
                        .uri("/links")
                        .header("Tg-Chat-Id", String.valueOf(chatId))
                        .body(new RemoveLinkRequest(normalizedLink))
                        .retrieve()
                        .onStatus(
                                statusCode -> LINK_NOT_FOUND_HTTP_STATUSES.contains(statusCode.value()),
                                (request, response) -> {
                                    throw new LinkNotFoundException();
                                })
                        .body(LinkResponse.class);
            } catch (LinkNotFoundException ex) {
                throw ex;
            } catch (RestClientException ex) {
                throw ScrapperClientErrorMapper.mapRestFailure("Failed to remove link in scrapper", ex);
            }
        });
    }

    /**
     * Получает список отслеживаемых ссылок чата через HTTP.
     *
     * @param chatId идентификатор чата
     * @return список ссылок чата (пустой, если ответ без данных)
     * @implNote Нужен для команды `/list`, чтобы корректно обрабатывать пустой ответ без `null` в вызывающем коде.
     */
    @Override
    public List<LinkResponse> getLinks(long chatId) {
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "getLinks", () -> {
            try {
                ListLinksResponse response = restClient
                        .get()
                        .uri("/list")
                        .header("Tg-Chat-Id", String.valueOf(chatId))
                        .retrieve()
                        .body(ListLinksResponse.class);
                return response == null || response.links() == null ? List.of() : response.links();
            } catch (RestClientException ex) {
                throw ScrapperClientErrorMapper.mapRestFailure("Failed to fetch links from scrapper", ex);
            }
        });
    }
}
