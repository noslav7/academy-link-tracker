package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import backend.academy.linktracker.ai.service.exception.AiServiceUnavailableException;
import backend.academy.linktracker.ai.service.exception.AiSummarizationBusinessException;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Суммаризирует длинные обновления через внешний AI API.
 */
@Service
public class AiApiUpdateSummarizer implements UpdateSummarizer {

    /**
     * Флаг Hugging Face Inference API: дождаться загрузки модели перед ответом.
     */
    private static final String WAIT_FOR_MODEL = "wait_for_model";

    /**
     * Парсер ответа AI API, нужен для выделения summary и доменной семантики ошибок ответа.
     */
    private final AiApiSummarizationResponseParser responseParser;
    /**
     * HTTP-клиент интеграции, нужен для отправки запросов во внешний AI API.
     */
    private final RestClient restClient;
    /**
     * Настройки суммаризации, нужны для URL, токена, таймаута и шаблона prompt.
     */
    private final AiAgentProperties.Summarization summarizationProperties;

    /**
     * Создает суммаризатор с HTTP-клиентом на базе конфигурации, чтобы вызывать внешний AI API с заданными таймаутами.
     *
     * @param restClientBuilder фабрика HTTP-клиента
     * @param responseParser парсер ответа AI API
     * @param aiAgentProperties настройки модуля
     */
    @Autowired
    public AiApiUpdateSummarizer(
            RestClient.Builder restClientBuilder,
            AiApiSummarizationResponseParser responseParser,
            AiAgentProperties aiAgentProperties) {
        this(
                restClientBuilder
                        .requestFactory(requestFactory(aiAgentProperties.getSummarization()))
                        .build(),
                responseParser,
                aiAgentProperties);
    }

    /**
     * Создает суммаризатор с явно переданным HTTP-клиентом, чтобы поддержать package-local сценарии и тестовые подстановки.
     *
     * @param restClient HTTP-клиент внешней интеграции
     * @param responseParser парсер ответа AI API
     * @param aiAgentProperties настройки модуля
     */
    AiApiUpdateSummarizer(
            RestClient restClient,
            AiApiSummarizationResponseParser responseParser,
            AiAgentProperties aiAgentProperties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser");
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
                    .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                        throw new AiSummarizationBusinessException("AI API rejected summarization request with status "
                                + clientResponse.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, clientResponse) -> {
                        throw new AiServiceUnavailableException("AI API is unavailable with status "
                                + clientResponse.getStatusCode().value());
                    })
                    .body(String.class);
            return responseParser.parseSummary(response);
        } catch (AiSummarizationBusinessException | AiServiceUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new AiServiceUnavailableException(
                    "AI API is unavailable or timed out (timeout=" + summarizationProperties.getTimeout() + ")", ex);
        } catch (RestClientException ex) {
            throw new AiServiceUnavailableException("Failed to call AI API due to transport error", ex);
        }
    }

    /**
     * Формирует спецификацию POST-запроса, чтобы централизованно задать URI и заголовки внешнего вызова.
     *
     * @return спецификация тела запроса
     */
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

    /**
     * Строит prompt для AI API, чтобы сохранить общий инструктивный контекст и добавить пользовательский текст.
     *
     * @param description исходный текст обновления
     * @return полный prompt запроса
     */
    private String prompt(String description) {
        return summarizationProperties.getPrompt()
                + System.lineSeparator()
                + System.lineSeparator()
                + description.trim();
    }

    /**
     * Создает request factory с таймаутами из конфигурации, чтобы внешние HTTP-вызовы имели ограниченный deadline.
     *
     * @param summarizationProperties настройки суммаризации
     * @return фабрика HTTP-запросов с таймаутами
     */
    private static SimpleClientHttpRequestFactory requestFactory(
            AiAgentProperties.Summarization summarizationProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(summarizationProperties.getTimeout());
        requestFactory.setReadTimeout(summarizationProperties.getTimeout());
        return requestFactory;
    }

    /**
     * Тело запроса Hugging Face Summarization API.
     *
     * @param inputs итоговый prompt
     * @param options флаги поведения модели
     */
    private record HuggingFaceSummarizationRequest(String inputs, Map<String, Boolean> options) {}
}
