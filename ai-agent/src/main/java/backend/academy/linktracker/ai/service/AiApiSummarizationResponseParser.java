package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.service.exception.AiServiceUnavailableException;
import backend.academy.linktracker.ai.service.exception.AiSummarizationBusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Извлекает summary из сырого ответа внешнего AI API.
 */
@Component
public class AiApiSummarizationResponseParser {

    /**
     * Максимально допустимая длина summary согласно контракту выходного DTO.
     */
    private static final int MAX_SUMMARY_LENGTH = 16_384;
    /**
     * Ключ поля summary в ответе AI API.
     */
    private static final String SUMMARY_TEXT = "summary_text";
    /**
     * Альтернативный ключ сгенерированного текста в ответе AI API.
     */
    private static final String GENERATED_TEXT = "generated_text";
    /**
     * Ключ поля ошибки в ответе AI API.
     */
    private static final String ERROR = "error";

    /**
     * JSON-парсер, нужен для чтения структуры ответа AI API.
     */
    private final ObjectMapper objectMapper;

    /**
     * Создает парсер ответа AI API, чтобы централизовать извлечение summary и валидацию результата.
     *
     * @param objectMapper JSON-парсер
     */
    public AiApiSummarizationResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Преобразует body ответа AI API в итоговый текст summary.
     *
     * @param responseBody сырой JSON body
     * @return извлеченный summary
     */
    public String parseSummary(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new AiServiceUnavailableException("AI API returned no response body");
        }

        try {
            return validateSummary(extractSummary(objectMapper.readTree(responseBody)));
        } catch (JsonProcessingException ex) {
            throw new AiSummarizationBusinessException("Failed to parse AI API response", ex);
        }
    }

    /**
     * Валидирует итоговый summary, чтобы downstream-сервис получил непустой текст в рамках контрактного размера.
     *
     * @param summary извлеченный текст summary
     * @return нормализованный summary
     */
    private String validateSummary(String summary) {
        if (!StringUtils.hasText(summary)) {
            throw new AiSummarizationBusinessException("AI API returned an empty summary");
        }
        String normalizedSummary = summary.trim();
        if (normalizedSummary.length() > MAX_SUMMARY_LENGTH) {
            throw new AiSummarizationBusinessException(
                    "AI API returned summary longer than allowed limit: " + MAX_SUMMARY_LENGTH);
        }
        return normalizedSummary;
    }

    /**
     * Извлекает summary из JSON-ответа, чтобы свести разные форматы ответа API к единому строковому результату.
     *
     * @param response JSON-ответ AI API
     * @return извлеченный summary
     */
    private String extractSummary(JsonNode response) {
        if (response == null || response.isNull()) {
            throw new AiServiceUnavailableException("AI API returned an empty response");
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
                throw new AiSummarizationBusinessException(
                        "AI API returned an error: " + response.get(ERROR).asText());
            }
        }
        throw new AiSummarizationBusinessException("AI API response does not contain a summary");
    }
}
