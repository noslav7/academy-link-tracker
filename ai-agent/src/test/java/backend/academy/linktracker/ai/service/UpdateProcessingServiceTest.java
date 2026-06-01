package backend.academy.linktracker.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.dto.RawLinkUpdate;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
class UpdateProcessingServiceTest {

    private AiAgentProperties properties;
    private CountingSummarizer summarizer;
    private UpdatePrioritizer prioritizer;
    private UpdateProcessingService service;

    @BeforeEach
    void setUp() {
        properties = defaultProperties();
        summarizer = new CountingSummarizer("short summary");
        prioritizer = new UpdatePrioritizer(properties);
        service = new UpdateProcessingService(new UpdateFilter(properties), summarizer, prioritizer, properties);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filteredOutCases")
    void givenFilteredByRulesWhenProcessThenReturnsEmptyResult(
            String ignoredCaseName, String description, String author) {
        // Цель: компактно проверить отбрасывание по всем правилам фильтрации.
        RawLinkUpdate update = update(description, author);
        Optional<ProcessedLinkUpdate> result = service.process(update);
        assertTrue(result.isEmpty());
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
                new ProcessedLinkUpdate(12345L, description, List.of(111L, 222L), UpdatePriority.MEDIUM),
                result.orElseThrow());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @MethodSource("priorityCases")
    void givenDescriptionForPriorityRulesWhenProcessThenDetectsExpectedPriority(
            String description, UpdatePriority expectedPriority) {
        // Цель: компактно проверить все ветки приоритизации (HIGH/MEDIUM/LOW) на конкретных данных.
        RawLinkUpdate update = update(description, "alice");

        Optional<ProcessedLinkUpdate> result = service.process(update);

        assertEquals(expectedPriority, result.orElseThrow().priority());
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

    @Test
    void givenLongUpdateWhenSummarizerFailsThenPropagatesException() {
        // Цель: проверить проброс ошибки суммаризатора без подавления.
        properties.getSummarization().setThreshold(10);
        RuntimeException expected = new RuntimeException("summarizer unavailable");
        UpdateProcessingService failingService = new UpdateProcessingService(
                new UpdateFilter(properties),
                description -> {
                    throw expected;
                },
                prioritizer,
                properties);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> failingService.process(
                        update("Long update text with enough details to require summary", "alice")));

        assertEquals(expected, actual);
    }

    private static AiAgentProperties defaultProperties() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.getFiltering().setStopWords(List.of("spam", "ads", "promo"));
        properties.getFiltering().setExcludedAuthors(List.of("bot-user"));
        properties.getFiltering().setMinLength(20);
        properties.getPrioritization().setHighKeywords(List.of("critical", "urgent", "breaking", "security"));
        properties.getPrioritization().setLowKeywords(List.of("minor", "typo", "chore", "docs"));
        properties.getSummarization().setThreshold(500);
        return properties;
    }

    private static RawLinkUpdate update(String description, String author) {
        return new RawLinkUpdate(12345L, description, author, List.of(111L, 222L));
    }

    private static Stream<Arguments> priorityCases() {
        return Stream.of(
                Arguments.of("Critical bug fix in release pipeline", UpdatePriority.HIGH),
                Arguments.of("Regular status update for release plan", UpdatePriority.MEDIUM),
                Arguments.of("Fix typo in readme docs section", UpdatePriority.LOW));
    }

    private static Stream<Arguments> filteredOutCases() {
        return Stream.of(
                Arguments.of(
                        "givenUpdateWithConfiguredStopWordWhenProcessThenReturnsEmptyResult",
                        "Release contains Promo materials and useful details",
                        "alice"),
                Arguments.of(
                        "givenUpdateFromExcludedAuthorWhenProcessThenReturnsEmptyResult",
                        "Regular update with enough useful details",
                        " BOT-USER "),
                Arguments.of(
                        "givenUpdateShorterThanMinimumLengthWhenProcessThenReturnsEmptyResult", "too short", "alice"));
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
