package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Суммаризирует длинные обновления через внешний AI API.
 */
@Service
public class AiApiUpdateSummarizer implements UpdateSummarizer {

    private static final String WAIT_FOR_MODEL = "wait_for_model";
    private static final String SUMMARY_TEXT = "summary_text";
    private static final String GENERATED_TEXT = "generated_text";
    private static final String ERROR = "error";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final AiAgentProperties.Summarization summarizationProperties;

    public AiApiUpdateSummarizer(RestClient.Builder restClientBuilder, AiAgentProperties aiAgentProperties) {
        this(
                restClientBuilder
                        .requestFactory(requestFactory(aiAgentProperties.getSummarization()))
                        .build(),
                aiAgentProperties);
    }

    AiApiUpdateSummarizer(RestClient restClient, AiAgentProperties aiAgentProperties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.summarizationProperties =
                Objects.requireNonNull(aiAgentProperties, "aiAgentProperties").getSummarization();
    }

    /**
     * Отправляет текст во внешний AI API с промптом из конфигурации.
     *
     * @param description исходный текст
     * @return сокращенный текст из ответа AI API
     */
    @Override
    public String summarize(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }

        try {
            String response = requestSpec()
                    .body(new HuggingFaceSummarizationRequest(prompt(description), Map.of(WAIT_FOR_MODEL, true)))
                    .retrieve()
                    .body(String.class);
            return extractSummary(objectMapper.readTree(response));
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to summarize update through AI API", ex);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse AI API response", ex);
        }
    }

    private RestClient.RequestBodySpec requestSpec() {
        RestClient.RequestBodySpec requestSpec = restClient
                .post()
                .uri(summarizationProperties.getApiUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(summarizationProperties.getApiToken())) {
            requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + summarizationProperties.getApiToken());
        }
        return requestSpec;
    }

    private String prompt(String description) {
        return summarizationProperties.getPrompt()
                + System.lineSeparator()
                + System.lineSeparator()
                + description.trim();
    }

    private String extractSummary(JsonNode response) {
        if (response == null || response.isNull()) {
            throw new IllegalStateException("AI API returned an empty response");
        }
        if (response.isArray() && !response.isEmpty()) {
            return extractSummary(response.get(0));
        }
        if (response.isObject()) {
            if (response.hasNonNull(SUMMARY_TEXT)) {
                return response.get(SUMMARY_TEXT).asText();
            }
            if (response.hasNonNull(GENERATED_TEXT)) {
                return response.get(GENERATED_TEXT).asText();
            }
            if (response.hasNonNull(ERROR)) {
                throw new IllegalStateException(
                        "AI API returned an error: " + response.get(ERROR).asText());
            }
        }
        throw new IllegalStateException("AI API response does not contain a summary");
    }

    private static SimpleClientHttpRequestFactory requestFactory(
            AiAgentProperties.Summarization summarizationProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(summarizationProperties.getTimeout());
        requestFactory.setReadTimeout(summarizationProperties.getTimeout());
        return requestFactory;
    }

    private record HuggingFaceSummarizationRequest(String inputs, Map<String, Boolean> options) {}
}
