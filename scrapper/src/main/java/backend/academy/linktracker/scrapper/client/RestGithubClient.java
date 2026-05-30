package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.metrics.ScrapperMetricsRecorder;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиент получения метаданных репозиториев из GitHub API.
 */
@Component
public class RestGithubClient implements GithubClient {
    /**
     * Резервное имя автора, которое используется при пустом значении в ответе API,
     * чтобы формировать стабильное сообщение для пользователя.
     */
    private static final String UNKNOWN_AUTHOR = "unknown";
    /**
     * Резервное тело issue/pr при отсутствии поля body в ответе,
     * чтобы избежать null в доменной модели.
     */
    private static final String EMPTY_BODY = "";
    /**
     * Ограничение на длину path-сегмента owner/repository.
     * Нужно для ранней валидации входа до HTTP-вызова.
     */
    private static final int MAX_SEGMENT_LENGTH = 255;

    private static final String SCOPE_EXTERNAL_SOURCE = "external_source";
    private static final String SCOPE_TYPE_GITHUB = "github";

    /**
     * Клиент HTTP-запросов в GitHub API.
     */
    private final RestClient restClient;
    /**
     * Настройки подключения к GitHub API.
     */
    private final GithubProperties properties;
    /**
     * Единый блок устойчивости HTTP-вызовов (retry).
     */
    private final ResilientHttpExecutor resilientHttpExecutor;
    /**
     * Recorder пользовательских метрик scrapper.
     */
    private final ScrapperMetricsRecorder metricsRecorder;

    /**
     * Создает REST-клиент GitHub API.
     *
     * @param restClient HTTP-клиент для GitHub API
     * @param properties конфигурация GitHub
     * @param resilientHttpExecutor компонент применения retry/fallback-политики
     */
    public RestGithubClient(
            @Qualifier("githubRestClient") RestClient restClient,
            GithubProperties properties,
            ResilientHttpExecutor resilientHttpExecutor) {
        this(restClient, properties, resilientHttpExecutor, ScrapperMetricsRecorder.noop());
    }

    @Autowired
    public RestGithubClient(
            @Qualifier("githubRestClient") RestClient restClient,
            GithubProperties properties,
            ResilientHttpExecutor resilientHttpExecutor,
            ScrapperMetricsRecorder metricsRecorder) {
        this.restClient = restClient;
        this.properties = properties;
        this.resilientHttpExecutor = resilientHttpExecutor;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Получает время последнего обновления репозитория.
     * Нужен для определения факта изменений без загрузки тяжелых данных.
     *
     * @param owner владелец репозитория
     * @param repository имя репозитория
     * @return время обновления или {@link Instant#EPOCH} при невалидном входе/ошибке API
     */
    @Override
    public Instant fetchRepositoryUpdatedAt(String owner, String repository) {
        return metricsRecorder.recordDuration(SCOPE_EXTERNAL_SOURCE, SCOPE_TYPE_GITHUB, () -> {
            if (!isValidRepositoryPathSegment(owner) || !isValidRepositoryPathSegment(repository)) {
                return Instant.EPOCH;
            }
            return resilientHttpExecutor.executeOrFallback(
                    () -> extractUpdatedAtOrEpoch(restClient
                            .get()
                            .uri("/repos/{owner}/{repository}", owner, repository)
                            .headers(this::applyAuthHeader)
                            .retrieve()
                            .body(GithubRepositoryResponse.class)),
                    () -> Instant.EPOCH);
        });
    }

    /**
     * Получает сведения о последнем issue или pull request репозитория.
     * Нужен для формирования человекочитаемого описания обновления.
     *
     * @param owner владелец репозитория
     * @param repository имя репозитория
     * @return данные последнего issue/pr или пустой результат при невалидном входе/ошибке API
     */
    @Override
    public Optional<GithubIssueOrPullRequestUpdate> fetchLatestIssueOrPullRequest(String owner, String repository) {
        return metricsRecorder.recordDuration(SCOPE_EXTERNAL_SOURCE, SCOPE_TYPE_GITHUB, () -> {
            if (!isValidRepositoryPathSegment(owner) || !isValidRepositoryPathSegment(repository)) {
                return Optional.empty();
            }
            return resilientHttpExecutor.executeOrFallback(
                    () -> mapLatestIssueOrPullRequest(restClient
                            .get()
                            .uri(
                                    "/repos/{owner}/{repository}/issues?state=all&sort=created&direction=desc&per_page=1",
                                    owner,
                                    repository)
                            .headers(this::applyAuthHeader)
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<GithubIssueResponse>>() {})),
                    Optional::empty);
        });
    }

    /**
     * Нормализует ответ GitHub API к безопасному времени обновления.
     * Нужен для единой fallback-семантики в одном месте.
     *
     * @param response ответ API репозитория
     * @return время обновления или {@link Instant#EPOCH}
     */
    private Instant extractUpdatedAtOrEpoch(GithubRepositoryResponse response) {
        if (response == null || response.updatedAt() == null) {
            return Instant.EPOCH;
        }
        return response.updatedAt();
    }

    /**
     * Преобразует список issues API в доменную модель последнего события.
     * Нужен для отделения сетевого вызова от маппинга результата.
     *
     * @param items список issues/pr из GitHub API
     * @return доменная модель последнего issue/pr или пустой результат
     */
    private Optional<GithubIssueOrPullRequestUpdate> mapLatestIssueOrPullRequest(List<GithubIssueResponse> items) {
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        GithubIssueResponse first = items.getFirst();
        if (first == null || !hasTextValue(first.title()) || first.createdAt() == null) {
            return Optional.empty();
        }

        String authorLogin =
                normalizeOrFallback(first.user() == null ? null : first.user().login(), UNKNOWN_AUTHOR);
        String body = first.body() == null ? EMPTY_BODY : first.body();
        boolean isIssue = first.pullRequest() == null;

        return Optional.of(
                new GithubIssueOrPullRequestUpdate(isIssue, first.title(), authorLogin, first.createdAt(), body));
    }

    /**
     * Проверяет корректность path-сегмента owner/repository.
     * Нужен для раннего отклонения потенциально некорректного URL.
     *
     * @param value значение сегмента пути
     * @return {@code true}, если значение допустимо для подстановки в URI
     */
    private static boolean isValidRepositoryPathSegment(String value) {
        return hasTextValue(value) && value.length() <= MAX_SEGMENT_LENGTH && !value.contains("/");
    }

    /**
     * Возвращает нормализованное строковое значение либо fallback.
     * Нужен для устранения null/blank на границе внешнего API.
     *
     * @param value исходное значение
     * @param fallback значение по умолчанию
     * @return исходное значение или fallback
     */
    private static String normalizeOrFallback(String value, String fallback) {
        if (!hasTextValue(value)) {
            return fallback;
        }
        return value;
    }

    /**
     * Проверяет, что строка не пуста и не состоит из пробелов.
     * Нужен для переиспользуемой валидации строковых полей.
     *
     * @param value проверяемая строка
     * @return {@code true}, если строка содержит полезный текст
     */
    private static boolean hasTextValue(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Внутренний DTO ответа GitHub API.
     * Нужен для десериализации ровно тех полей, которые участвуют в сравнении времени обновления.
     *
     * @param updatedAt время обновления репозитория
     */
    private record GithubRepositoryResponse(
            @JsonProperty("updated_at") Instant updatedAt) {}

    /**
     * Внутренний DTO элемента списка issue/pr из GitHub API.
     * Нужен для локального маппинга API-ответа без утечки транспортной модели наружу.
     */
    private record GithubIssueResponse(
            String title,
            String body,
            GithubUser user,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("pull_request") Object pullRequest) {}

    /**
     * Внутренний DTO автора issue/pr из GitHub API.
     * Нужен для безопасного извлечения логина автора в уведомление.
     */
    private record GithubUser(String login) {}

    /**
     * Добавляет Bearer-токен в заголовки запроса, если токен задан в конфигурации.
     * Нужен для доступа к приватным/лимитируемым данным GitHub API при наличии токена.
     *
     * @param headers HTTP-заголовки запроса
     */
    private void applyAuthHeader(HttpHeaders headers) {
        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            headers.setBearerAuth(properties.getToken());
        }
    }
}
