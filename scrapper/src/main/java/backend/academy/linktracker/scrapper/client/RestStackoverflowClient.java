package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиент получения метаданных вопросов из Stack Overflow API.
 */
@Component
public class RestStackoverflowClient implements StackoverflowClient {

    /**
     * Клиент HTTP-запросов к Stack Overflow API.
     */
    private final RestClient restClient;
    /**
     * Настройки подключения к Stack Overflow API.
     */
    private final StackoverflowProperties properties;

    /**
     * Создает REST-клиент Stack Overflow API.
     *
     * @param restClient HTTP-клиент Stack Overflow
     * @param properties настройки подключения
     */
    public RestStackoverflowClient(
            @Qualifier("stackoverflowRestClient") RestClient restClient, StackoverflowProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant fetchQuestionUpdatedAt(long questionId) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("site", "stackoverflow");
        if (properties.getKey() != null && !properties.getKey().isBlank()) {
            params.add("key", properties.getKey());
        }
        if (properties.getAccessToken() != null && !properties.getAccessToken().isBlank()) {
            params.add("access_token", properties.getAccessToken());
        }

        try {
            StackoverflowResponse response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/questions/{id}")
                            .queryParams(params)
                            .build(questionId))
                    .retrieve()
                    .body(StackoverflowResponse.class);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                return Instant.EPOCH;
            }
            Long lastActivity = response.items().getFirst().lastActivityDate();
            return lastActivity == null ? Instant.EPOCH : Instant.ofEpochSecond(lastActivity);
        } catch (RuntimeException ex) {
            return Instant.EPOCH;
        }
    }

    /**
     * DTO ответа Stack Overflow API.
     *
     * @param items список элементов ответа
     */
    private record StackoverflowResponse(List<StackoverflowItem> items) {}

    /**
     * DTO элемента ответа Stack Overflow API.
     *
     * @param lastActivityDate время последней активности в epoch seconds
     */
    private record StackoverflowItem(
            @JsonProperty("last_activity_date") Long lastActivityDate) {}
}
