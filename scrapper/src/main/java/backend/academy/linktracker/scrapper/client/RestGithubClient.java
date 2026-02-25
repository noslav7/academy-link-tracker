package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestGithubClient implements GithubClient {

    private final RestClient restClient;
    private final GithubProperties properties;

    public RestGithubClient(@Qualifier("githubRestClient") RestClient restClient, GithubProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Instant fetchRepositoryUpdatedAt(String owner, String repository) {
        GithubRepositoryResponse response = restClient
                .get()
                .uri("/repos/{owner}/{repository}", owner, repository)
                .headers(headers -> {
                    if (properties.getToken() != null && !properties.getToken().isBlank()) {
                        headers.setBearerAuth(properties.getToken());
                    }
                })
                .retrieve()
                .body(GithubRepositoryResponse.class);

        if (response == null || response.updatedAt() == null) {
            return Instant.EPOCH;
        }
        return response.updatedAt();
    }

    private record GithubRepositoryResponse(@JsonProperty("updated_at") java.time.Instant updatedAt) {}
}
