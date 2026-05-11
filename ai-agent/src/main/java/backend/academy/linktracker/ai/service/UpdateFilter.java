package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.RawLinkUpdate;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Проверяет входящее обновление по пользовательским правилам фильтрации.
 */
@Service
@Validated
public class UpdateFilter {

    /**
     * Функция нормализации текста, нужна для единообразных сравнений без учета регистра и краевых пробелов.
     */
    private static final UnaryOperator<String> NORMALIZE_TEXT =
            value -> value.trim().toLowerCase(Locale.ROOT);

    /**
     * Настройки фильтрации, нужны для применения правил без жестко заданных значений в коде.
     */
    private final AiAgentProperties aiAgentProperties;

    /**
     * Создает фильтр с настройками AI Agent, чтобы правила можно было менять через конфигурацию.
     *
     * @param aiAgentProperties настройки AI Agent
     */
    public UpdateFilter(AiAgentProperties aiAgentProperties) {
        this.aiAgentProperties = aiAgentProperties;
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
        String normalizedDescription = NORMALIZE_TEXT.apply(description);
        Set<String> normalizedStopWords =
                normalizedConfiguredValues(aiAgentProperties.getFiltering().getStopWords());
        return normalizedStopWords.stream().anyMatch(normalizedDescription::contains);
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

        String normalizedAuthor = NORMALIZE_TEXT.apply(author);
        Set<String> normalizedExcludedAuthors =
                normalizedConfiguredValues(aiAgentProperties.getFiltering().getExcludedAuthors());
        return normalizedExcludedAuthors.contains(normalizedAuthor);
    }

    /**
     * Нормализует значения конфигурации в неизменяемое множество, чтобы проверки были единообразными.
     *
     * @param values исходные значения из конфигурации
     * @return нормализованные непустые значения
     */
    private static Set<String> normalizedConfiguredValues(Collection<String> values) {
        if (values == null) {
            return Collections.emptySet();
        }

        Set<String> normalizedValues = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalizedValues.add(NORMALIZE_TEXT.apply(value));
            }
        }
        return Collections.unmodifiableSet(normalizedValues);
    }
}
