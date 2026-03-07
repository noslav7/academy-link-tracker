package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиент получения метаданных репозиториев из GitHub API.
 */
@Component
public class RestGithubClient implements GithubClient {

    /**
     * Клиент HTTP-запросов в GitHub API.
     */
    private final RestClient restClient;
    /**
     * Настройки подключения к GitHub API.
     */
    private final GithubProperties properties;

    /**
     * Создает REST-клиент GitHub API.
     *
     * @param restClient HTTP-клиент для GitHub API
     * @param properties конфигурация GitHub
     */
    public RestGithubClient(@Qualifier("githubRestClient") RestClient restClient, GithubProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant fetchRepositoryUpdatedAt(String owner, String repository) {
        try {
            GithubRepositoryResponse response = restClient
                    .get()
                    .uri("/repos/{owner}/{repository}", owner, repository)
                    .headers(headers -> {
                        if (properties.getToken() != null
                                && !properties.getToken().isBlank()) {
                            headers.setBearerAuth(properties.getToken());
                        }
                    })
                    .retrieve()
                    .body(GithubRepositoryResponse.class);

            if (response == null || response.updatedAt() == null) {
                return Instant.EPOCH;
            }
            return response.updatedAt();
        } catch (RuntimeException ex) {
            return Instant.EPOCH;
        }
    }

    /**
     * Внутренний DTO ответа GitHub API.
     *
     * @param updatedAt время обновления репозитория
     */
    private record GithubRepositoryResponse(
            @JsonProperty("updated_at") java.time.Instant updatedAt) {}
}
