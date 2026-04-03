package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
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
                    .headers(this::applyAuthHeader)
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
     * {@inheritDoc}
     */
    @Override
    public Optional<GithubIssueOrPullRequestUpdate> fetchLatestIssueOrPullRequest(String owner, String repository) {
        try {
            List<GithubIssueResponse> items = restClient
                    .get()
                    .uri(
                            "/repos/{owner}/{repository}/issues?state=all&sort=created&direction=desc&per_page=1",
                            owner,
                            repository)
                    .headers(this::applyAuthHeader)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<GithubIssueResponse>>() {});

            if (items == null || items.isEmpty()) {
                return Optional.empty();
            }
            GithubIssueResponse first = items.getFirst();
            if (first == null || first.title() == null || first.createdAt() == null) {
                return Optional.empty();
            }

            String authorLogin = first.user() == null || first.user().login() == null
                    ? "unknown"
                    : first.user().login();
            String body = first.body() == null ? "" : first.body();
            boolean issue = first.pullRequest() == null;

            return Optional.of(
                    new GithubIssueOrPullRequestUpdate(issue, first.title(), authorLogin, first.createdAt(), body));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * Внутренний DTO ответа GitHub API.
     *
     * @param updatedAt время обновления репозитория
     */
    private record GithubRepositoryResponse(
            @JsonProperty("updated_at") Instant updatedAt) {}

    private record GithubIssueResponse(
            String title,
            String body,
            GithubUser user,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("pull_request") Object pullRequest) {}

    private record GithubUser(String login) {}

    /**
     * Добавляет Bearer-токен в заголовки запроса, если токен задан в конфигурации.
     *
     * @param headers HTTP-заголовки запроса
     */
    private void applyAuthHeader(HttpHeaders headers) {
        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            headers.setBearerAuth(properties.getToken());
        }
    }
}
