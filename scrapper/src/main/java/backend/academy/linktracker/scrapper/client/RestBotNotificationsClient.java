package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.metrics.ScrapperMetricsRecorder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP-клиент отправки уведомлений в bot-сервис.
 */
public class RestBotNotificationsClient implements BotNotificationsClient {
    private static final String SCOPE_EXTERNAL_SOURCE = "external_source";
    private static final String SCOPE_TYPE_BOT_HTTP = "bot_http";

    /**
     * Клиент HTTP-вызовов bot-сервиса.
     */
    private final RestClient restClient;

    private final ScrapperMetricsRecorder metricsRecorder;

    /**
     * Создает клиент уведомлений по REST.
     *
     * @param restClient HTTP-клиент для запросов в bot
     */
    public RestBotNotificationsClient(@Qualifier("botRestClient") RestClient restClient) {
        this(restClient, ScrapperMetricsRecorder.noop());
    }

    public RestBotNotificationsClient(
            @Qualifier("botRestClient") RestClient restClient, ScrapperMetricsRecorder metricsRecorder) {
        this.restClient = restClient;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendUpdate(LinkUpdate update) {
        metricsRecorder.recordDuration(SCOPE_EXTERNAL_SOURCE, SCOPE_TYPE_BOT_HTTP, () -> {
            try {
                return restClient.post().uri("/updates").body(update).retrieve().toBodilessEntity();
            } catch (RestClientException exception) {
                throw new BotServiceUnavailableException("Failed to send update to bot over HTTP", exception);
            }
        });
    }
}
