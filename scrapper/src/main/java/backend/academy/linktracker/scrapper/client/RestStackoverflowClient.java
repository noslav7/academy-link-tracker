package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиент получения метаданных вопросов из Stack Overflow API.
 */
@Component
public class RestStackoverflowClient implements StackoverflowClient {
    private static final String SITE = "stackoverflow";

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
        LinkedMultiValueMap<String, String> params = defaultParams();

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
     * {@inheritDoc}
     */
    @Override
    public Optional<StackoverflowAnswerOrCommentUpdate> fetchLatestAnswerOrCommentUpdate(long questionId) {
        try {
            LinkedMultiValueMap<String, String> questionParams = defaultParams();
            questionParams.add("filter", "withbody");
            StackoverflowResponse questionResponse = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/questions/{id}")
                            .queryParams(questionParams)
                            .build(questionId))
                    .retrieve()
                    .body(StackoverflowResponse.class);

            if (questionResponse == null || questionResponse.items() == null || questionResponse.items().isEmpty()) {
                return Optional.empty();
            }
            StackoverflowItem question = questionResponse.items().getFirst();
            String questionTitle = question.title() == null ? "Без названия" : question.title();

            Optional<StackoverflowEvent> latestAnswer = fetchLatestAnswer(questionId);
            Optional<StackoverflowEvent> latestComment = fetchLatestComment(questionId);

            Optional<StackoverflowEvent> latestEvent = selectLatest(latestAnswer, latestComment);
            if (latestEvent.isEmpty()) {
                return Optional.empty();
            }
            StackoverflowEvent event = latestEvent.get();
            return Optional.of(new StackoverflowAnswerOrCommentUpdate(
                    questionTitle,
                    event.answer(),
                    event.authorDisplayName(),
                    event.createdAt(),
                    event.body()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<StackoverflowEvent> fetchLatestAnswer(long questionId) {
        LinkedMultiValueMap<String, String> params = defaultParams();
        params.add("order", "desc");
        params.add("sort", "creation");
        params.add("filter", "withbody");
        params.add("pagesize", "1");
        StackoverflowResponse response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/questions/{id}/answers")
                        .queryParams(params)
                        .build(questionId))
                .retrieve()
                .body(StackoverflowResponse.class);

        if (response == null || response.items() == null || response.items().isEmpty()) {
            return Optional.empty();
        }
        StackoverflowItem item = response.items().getFirst();
        if (item.creationDate() == null) {
            return Optional.empty();
        }
        return Optional.of(new StackoverflowEvent(
                true,
                item.owner() != null && item.owner().displayName() != null
                        ? item.owner().displayName()
                        : "unknown",
                Instant.ofEpochSecond(item.creationDate()),
                item.body() == null ? "" : item.body()));
    }

    private Optional<StackoverflowEvent> fetchLatestComment(long questionId) {
        LinkedMultiValueMap<String, String> params = defaultParams();
        params.add("order", "desc");
        params.add("sort", "creation");
        params.add("filter", "withbody");
        params.add("pagesize", "1");
        StackoverflowResponse response = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/questions/{id}/comments")
                        .queryParams(params)
                        .build(questionId))
                .retrieve()
                .body(StackoverflowResponse.class);

        if (response == null || response.items() == null || response.items().isEmpty()) {
            return Optional.empty();
        }
        StackoverflowItem item = response.items().getFirst();
        if (item.creationDate() == null) {
            return Optional.empty();
        }
        return Optional.of(new StackoverflowEvent(
                false,
                item.owner() != null && item.owner().displayName() != null
                        ? item.owner().displayName()
                        : "unknown",
                Instant.ofEpochSecond(item.creationDate()),
                item.body() == null ? "" : item.body()));
    }

    private static Optional<StackoverflowEvent> selectLatest(
            Optional<StackoverflowEvent> answer, Optional<StackoverflowEvent> comment) {
        if (answer.isEmpty()) {
            return comment;
        }
        if (comment.isEmpty()) {
            return answer;
        }
        return answer.get().createdAt().isAfter(comment.get().createdAt()) ? answer : comment;
    }

    private LinkedMultiValueMap<String, String> defaultParams() {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("site", SITE);
        if (properties.getKey() != null && !properties.getKey().isBlank()) {
            params.add("key", properties.getKey());
        }
        if (properties.getAccessToken() != null && !properties.getAccessToken().isBlank()) {
            params.add("access_token", properties.getAccessToken());
        }
        return params;
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
            @JsonProperty("last_activity_date") Long lastActivityDate,
            @JsonProperty("creation_date") Long creationDate,
            String title,
            String body,
            StackoverflowOwner owner) {}

    private record StackoverflowOwner(@JsonProperty("display_name") String displayName) {}

    private record StackoverflowEvent(boolean answer, String authorDisplayName, Instant createdAt, String body) {}
}
