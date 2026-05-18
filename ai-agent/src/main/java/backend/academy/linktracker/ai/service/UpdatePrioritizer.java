package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.UpdatePriority;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Вычисляет приоритет обновления по ключевым словам из пользовательской конфигурации.
 */
@Service
public class UpdatePrioritizer {

    private final Set<String> normalizedHighKeywords;
    private final Set<String> normalizedLowKeywords;

    public UpdatePrioritizer(AiAgentProperties aiAgentProperties) {
        this.normalizedHighKeywords = TextNormalizationUtils.normalizedConfiguredValues(
                aiAgentProperties.getPrioritization().getHighKeywords());
        this.normalizedLowKeywords = TextNormalizationUtils.normalizedConfiguredValues(
                aiAgentProperties.getPrioritization().getLowKeywords());
    }

    /**
     * Возвращает приоритет по правилам:
     * HIGH — если есть high-keyword;
     * LOW — если нет high и есть low-keyword;
     * MEDIUM — иначе.
     */
    public UpdatePriority detectPriority(String description) {
        String normalizedDescription = TextNormalizationUtils.normalize(Objects.toString(description, ""));
        if (TextNormalizationUtils.containsAnySubstring(normalizedDescription, normalizedHighKeywords)) {
            return UpdatePriority.HIGH;
        }
        if (TextNormalizationUtils.containsAnySubstring(normalizedDescription, normalizedLowKeywords)) {
            return UpdatePriority.LOW;
        }
        return UpdatePriority.MEDIUM;
    }
}
