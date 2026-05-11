package backend.academy.linktracker.ai.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки фильтрации и суммаризации AI Agent.
 */
@ConfigurationProperties(prefix = "ai-agent")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class AiAgentProperties {

    /**
     * Настройки фильтрации, нужны для отбрасывания незначимых обновлений до суммаризации.
     */
    @Valid
    @NotNull
    private Filtering filtering = new Filtering();

    /**
     * Настройки суммаризации, нужны для управления моментом сокращения длинного описания.
     */
    @Valid
    @NotNull
    private Summarization summarization = new Summarization();

    /**
     * Правила отбрасывания входящих обновлений.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Filtering {

        /**
         * Стоп-слова, нужны для исключения нежелательных обновлений по содержимому описания.
         */
        private List<String> stopWords = List.of();

        /**
         * Исключенные авторы, нужны для фильтрации обновлений от недоверенных источников.
         */
        private List<String> excludedAuthors = List.of();

        /**
         * Минимальная длина описания, нужна для отсечения слишком коротких обновлений.
         */
        @PositiveOrZero
        private int minLength = 0;
    }

    /**
     * Параметры суммаризации длинных обновлений.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Summarization {

        /**
         * Порог длины описания, нужен для решения о вызове суммаризации.
         */
        @PositiveOrZero
        private int threshold = 500;
    }
}
