package backend.academy.linktracker.ai.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки фильтрации, суммаризации, приоритизации и группировки AI Agent.
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
     * Настройки приоритизации, нужны для вычисления важности обновлений по ключевым словам.
     */
    @Valid
    @NotNull
    private Prioritization prioritization = new Prioritization();

    /**
     * Настройки группировки, нужны для объединения связанных обновлений в рамках временного окна.
     */
    @Valid
    @NotNull
    private Grouping grouping = new Grouping();

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

        /**
         * URL внешнего AI API, совместимый с Hugging Face Inference API.
         */
        @NotBlank
        private String apiUrl = "https://api-inference.huggingface.co/models/facebook/bart-large-cnn";

        /**
         * Bearer token внешнего AI API. Пустое значение оставляет запрос без Authorization header.
         */
        private String apiToken = "";

        /**
         * Промпт, который добавляется перед исходным текстом обновления.
         */
        @NotBlank
        private String prompt = "Summarize the following update in 2-3 sentences.";

        /**
         * Таймаут подключения и чтения ответа AI API.
         */
        @NotNull
        @DurationMin(millis = 1)
        private Duration timeout = Duration.ofSeconds(30);
    }

    /**
     * Параметры приоритизации обновлений по содержимому текста.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Prioritization {

        /**
         * Ключевые слова высокого приоритета.
         */
        private List<String> highKeywords = List.of("critical", "urgent", "breaking", "security");

        /**
         * Ключевые слова низкого приоритета.
         */
        private List<String> lowKeywords = List.of("minor", "typo", "chore", "docs");
    }

    /**
     * Параметры оконной группировки по tgChatId.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Grouping {

        /**
         * Размер окна группировки в миллисекундах.
         */
        @PositiveOrZero
        private long windowMs = 30_000;
    }
}
