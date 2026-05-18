package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.RawLinkUpdate;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Проверяет входящее обновление по пользовательским правилам фильтрации.
 */
@Service
@Validated
public class UpdateFilter {

    /**
     * Настройки фильтрации, нужны для применения правил без жестко заданных значений в коде.
     */
    private final AiAgentProperties aiAgentProperties;
    /**
     * Нормализованные стоп-слова из конфигурации для переиспользуемых membership-проверок.
     */
    private final Set<String> normalizedStopWords;
    /**
     * Нормализованные исключенные авторы из конфигурации для membership-проверок.
     */
    private final Set<String> normalizedExcludedAuthors;

    /**
     * Создает фильтр с настройками AI Agent, чтобы правила можно было менять через конфигурацию.
     *
     * @param aiAgentProperties настройки AI Agent
     */
    public UpdateFilter(AiAgentProperties aiAgentProperties) {
        this.aiAgentProperties = aiAgentProperties;
        this.normalizedStopWords = TextNormalizationUtils.normalizedConfiguredValues(
                aiAgentProperties.getFiltering().getStopWords());
        this.normalizedExcludedAuthors = TextNormalizationUtils.normalizedConfiguredValues(
                aiAgentProperties.getFiltering().getExcludedAuthors());
    }

    /**
     * Возвращает true, если обновление должно перейти на следующий этап, чтобы пайплайн не обрабатывал шум.
     *
     * @param update входящее обновление
     * @return результат фильтрации
     */
    public boolean shouldProcess(@Valid RawLinkUpdate update) {
        Objects.requireNonNull(update, "update");

        String description = Objects.toString(update.description(), "");
        return hasMinimumLength(description) && !containsStopWord(description) && !isExcludedAuthor(update.author());
    }

    /**
     * Проверяет минимальную длину описания, чтобы отбрасывать слишком короткие и малополезные обновления.
     *
     * @param description описание обновления
     * @return true, если описание не короче настроенного порога
     */
    private boolean hasMinimumLength(String description) {
        return description.trim().length() >= aiAgentProperties.getFiltering().getMinLength();
    }

    /**
     * Проверяет наличие стоп-слов, чтобы не пропускать заведомо нежелательные обновления дальше по пайплайну.
     *
     * @param description описание обновления
     * @return true, если описание содержит настроенное стоп-слово
     */
    private boolean containsStopWord(String description) {
        String normalizedDescription = TextNormalizationUtils.normalize(description);
        return TextNormalizationUtils.containsAnySubstring(normalizedDescription, normalizedStopWords);
    }

    /**
     * Проверяет автора обновления, чтобы исключать источники, которым сервис не должен доверять.
     *
     * @param author автор обновления
     * @return true, если автор входит в список исключений
     */
    private boolean isExcludedAuthor(String author) {
        if (author == null || author.isBlank()) {
            return false;
        }

        String normalizedAuthor = TextNormalizationUtils.normalize(author);
        return normalizedExcludedAuthors.contains(normalizedAuthor);
    }
}
