package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class RestStackoverflowClient implements StackoverflowClient {

    private final RestClient restClient;
    private final StackoverflowProperties properties;

    public RestStackoverflowClient(
            @Qualifier("stackoverflowRestClient") RestClient restClient, StackoverflowProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

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
    }

    private record StackoverflowResponse(List<StackoverflowItem> items) {}

    private record StackoverflowItem(@JsonProperty("last_activity_date") Long lastActivityDate) {}
}
