package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.client.dto.AddLinkRequest;
import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.bot.client.dto.ListLinksResponse;
import backend.academy.linktracker.bot.client.dto.RemoveLinkRequest;
import backend.academy.linktracker.bot.metrics.BotMetricsRecorder;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP-реализация клиента взаимодействия с scrapper API.
 */
public class RestScrapperClient implements ScrapperClient {
    private static final String SCOPE_SCRAPPER_SYNC_API = "scrapper_sync_api";

    /**
     * REST-клиент вызовов scrapper API.
     */
    private final RestClient restClient;

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
     * {@inheritDoc}
     */
    @Override
    public void registerChat(long chatId) {
        metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "registerChat", () -> {
            try {
                restClient
                        .post()
                        .uri("/tg-chat/{id}", chatId)
                        .retrieve()
                        .onStatus(statusCode -> statusCode.value() == 409, (request, response) -> {})
                        .toBodilessEntity();
            } catch (RestClientException ex) {
                throw new ScrapperServiceUnavailableException("Failed to register chat in scrapper", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "addLink", () -> {
            try {
                return restClient
                        .post()
                        .uri("/links")
                        .header("Tg-Chat-Id", String.valueOf(chatId))
                        .body(new AddLinkRequest(link, tags, filters))
                        .retrieve()
                        .onStatus(statusCode -> statusCode.value() == 409, (request, response) -> {
                            throw new LinkAlreadyTrackedException();
                        })
                        .body(LinkResponse.class);
            } catch (LinkAlreadyTrackedException ex) {
                throw ex;
            } catch (RestClientException ex) {
                throw new ScrapperServiceUnavailableException("Failed to add link in scrapper", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkResponse removeLink(long chatId, String link) {
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "removeLink", () -> {
            try {
                return restClient
                        .method(HttpMethod.DELETE)
                        .uri("/links")
                        .header("Tg-Chat-Id", String.valueOf(chatId))
                        .body(new RemoveLinkRequest(link))
                        .retrieve()
                        .onStatus(statusCode -> statusCode.value() == 404, (request, response) -> {
                            throw new LinkNotFoundException();
                        })
                        .body(LinkResponse.class);
            } catch (LinkNotFoundException ex) {
                throw ex;
            } catch (RestClientException ex) {
                throw new ScrapperServiceUnavailableException("Failed to remove link in scrapper", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
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
                throw new ScrapperServiceUnavailableException("Failed to fetch links from scrapper", ex);
            }
        });
    }
}
