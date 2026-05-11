package backend.academy.linktracker.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.dto.RawLinkUpdate;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class UpdateProcessingServiceTest {

    private AiAgentProperties properties;
    private CountingSummarizer summarizer;
    private UpdateProcessingService service;

    @BeforeEach
    void setUp() {
        properties = defaultProperties();
        summarizer = new CountingSummarizer("short summary");
        service = new UpdateProcessingService(new UpdateFilter(properties), summarizer, properties);
    }

    @Test
    void givenUpdateWithConfiguredStopWordWhenProcessThenReturnsEmptyResult() {
        // Цель: проверить отбрасывание обновления по стоп-слову из конфигурации.
        RawLinkUpdate update = update("Release contains Promo materials and useful details", "alice");

        Optional<ProcessedLinkUpdate> result = service.process(update);

        assertFalse(result.isPresent());
    }

    @Test
    void givenUpdateFromExcludedAuthorWhenProcessThenReturnsEmptyResult() {
        // Цель: проверить отбрасывание автора без учета регистра и краевых пробелов.
        RawLinkUpdate update = update("Regular update with enough useful details", " BOT-USER ");

        Optional<ProcessedLinkUpdate> result = service.process(update);

        assertFalse(result.isPresent());
    }

    @Test
    void givenUpdateShorterThanMinimumLengthWhenProcessThenReturnsEmptyResult() {
        // Цель: проверить отбрасывание описания короче минимальной длины.
        RawLinkUpdate update = update("too short", "alice");

        Optional<ProcessedLinkUpdate> result = service.process(update);

        assertFalse(result.isPresent());
    }

    @Test
    void givenUpdateWithMinimumAllowedLengthWhenProcessThenKeepsUpdate() {
        // Цель: проверить нижнюю границу minLength, где описание еще допустимо.
        String description = "12345678901234567890";
        RawLinkUpdate update = update(description, "alice");

        Optional<ProcessedLinkUpdate> result = service.process(update);

        assertEquals(description, result.orElseThrow().description());
    }

    @Test
    void givenValidUpdateWhenProcessThenReturnsProcessedUpdateForNextStage() {
        // Цель: проверить полный DTO-контракт результата для валидного обновления.
        String description = "Regular update with enough useful details";
        RawLinkUpdate update = update(description, "alice");

        Optional<ProcessedLinkUpdate> result = service.process(update);

        assertEquals(
                new ProcessedLinkUpdate(12345L, description, List.of(111L, 222L), UpdatePriority.HIGH),
                result.orElseThrow());
    }

    @Test
    void givenLongUpdateWhenProcessThenSummarizesDescription() {
        // Цель: проверить замену длинного описания результатом суммаризации.
        properties.getSummarization().setThreshold(30);
        String description = "Long update text with enough details to require summary";
        RawLinkUpdate update = update(description, "alice");

        Optional<ProcessedLinkUpdate> result = service.process(update);

        assertEquals("short summary", result.orElseThrow().description());
    }

    @Test
    void givenLongUpdateWhenProcessThenCallsSummarizerOnce() {
        // Цель: проверить, что длинное описание проходит этап суммаризации ровно один раз.
        properties.getSummarization().setThreshold(30);
        RawLinkUpdate update = update("Long update text with enough details to require summary", "alice");

        service.process(update);

        assertEquals(1, summarizer.calls());
    }

    @Test
    void givenUpdateLengthEqualsSummarizationThresholdWhenProcessThenKeepsOriginalDescription() {
        // Цель: проверить границу threshold, где суммаризация еще не запускается.
        String description = "Regular update with enough useful details";
        properties.getSummarization().setThreshold(description.length());
        RawLinkUpdate update = update(description, "alice");

        Optional<ProcessedLinkUpdate> result = service.process(update);

        assertEquals(description, result.orElseThrow().description());
    }

    @Test
    void givenFilteredUpdateWhenProcessThenDoesNotCallSummarizer() {
        // Цель: проверить, что отфильтрованное событие не проходит дорогой этап суммаризации.
        RawLinkUpdate update = update("Release contains promo materials and useful details", "alice");

        service.process(update);

        assertEquals(0, summarizer.calls());
    }

    @Test
    void givenNullUpdateWhenProcessThenThrowsNullPointerException() {
        // Цель: проверить явную ошибку контракта при null-входе.
        assertEquals(
                "update",
                assertThrows(NullPointerException.class, () -> service.process(null))
                        .getMessage());
    }

    private static AiAgentProperties defaultProperties() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.getFiltering().setStopWords(List.of("spam", "ads", "promo"));
        properties.getFiltering().setExcludedAuthors(List.of("bot-user"));
        properties.getFiltering().setMinLength(20);
        properties.getSummarization().setThreshold(500);
        return properties;
    }

    private static RawLinkUpdate update(String description, String author) {
        return new RawLinkUpdate(12345L, description, author, List.of(111L, 222L));
    }

    private static final class CountingSummarizer implements UpdateSummarizer {

        private final String summary;
        private int calls;

        private CountingSummarizer(String summary) {
            this.summary = summary;
        }

        @Override
        public String summarize(String description) {
            calls++;
            return summary;
        }

        private int calls() {
            return calls;
        }
    }
}
