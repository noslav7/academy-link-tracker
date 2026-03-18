package backend.academy.linktracker.scrapper.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = {ScrapperApplication.class, TestcontainersConfiguration.class})
@Tag("integration")
abstract class AbstractTrackedLinkRepositoryIntegrationTest {

    @Autowired
    protected TrackedLinkRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected abstract Class<?> expectedRepositoryType();

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from chats");
    }

    // Проверяет, что при заданном access-type поднимается ожидаемая реализация репозитория.
    @Test
    @Tag("sanity")
    @DisplayName("Given access type When context starts Then expected implementation is used")
    void givenAccessTypeWhenContextStartsThenExpectedImplementationIsUsed() {
        assertInstanceOf(expectedRepositoryType(), repository);
    }

    // Проверяет базовый сценарий сохранения и чтения ссылки.
    @Test
    void givenTrackedLinkWhenSavedThenLinkIsReadBackWithExpectedState() {
        assertTrue(repository.registerChat(1L));

        TrackedLink saved = repository.save(
                1L,
                "https://github.com/user/repo",
                List.of("work"),
                List.of("lang=java"),
                Instant.parse("2026-03-01T00:00:00Z"));

        List<TrackedLink> links = repository.findAllByChatId(1L);
        assertAll(
                () -> assertEquals(1, links.size()),
                () -> assertTrackedLinkEqualsIgnoringDbTimePrecision(saved, links.getFirst()),
                () -> assertTrackedLinkEqualsIgnoringDbTimePrecision(
                        saved,
                        repository
                                .findByChatIdAndUrl(1L, "https://github.com/user/repo")
                                .orElseThrow()));
    }

    // Проверяет удаление существующей ссылки.
    @Test
    void givenExistingTrackedLinkWhenDeletedThenLinkIsAbsent() {
        assertTrue(repository.registerChat(1L));
        TrackedLink saved = repository.save(
                1L,
                "https://github.com/user/repo",
                List.of("work"),
                List.of("lang=java"),
                Instant.parse("2026-03-01T00:00:00Z"));

        assertAll(
                () -> assertTrue(repository.deleteByChatIdAndUrl(1L, saved.url())),
                () -> assertEquals(List.of(), repository.findAllByChatId(1L)));
    }

    // Проверяет, что дублирующая ссылка приводит к ошибке ограничения уникальности.
    @Test
    void givenDuplicateLinkWhenSavedTwiceThenErrorIsThrown() {
        assertTrue(repository.registerChat(2L));
        repository.save(
                2L, "https://github.com/user/repo", List.of(), List.of(), Instant.parse("2026-03-01T00:00:00Z"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.save(
                        2L,
                        "https://github.com/user/repo",
                        List.of(),
                        List.of(),
                        Instant.parse("2026-03-01T00:00:00Z")));
    }

    // Проверяет обновление временных меток существующей ссылки.
    @Test
    void givenTrackedLinkWhenUpdatedThenTimestampsArePersisted() {
        assertTrue(repository.registerChat(3L));
        TrackedLink saved = repository.save(
                3L, "https://github.com/user/project", List.of(), List.of(), Instant.parse("2026-03-01T00:00:00Z"));

        TrackedLink updated =
                saved.withLastCheck(Instant.parse("2026-03-02T10:15:30Z"), Instant.parse("2026-03-02T10:15:00Z"));
        repository.updateTrackedLink(updated);

        TrackedLink actual = repository
                .findByChatIdAndUrl(3L, "https://github.com/user/project")
                .orElseThrow();
        assertTrackedLinkEqualsIgnoringDbTimePrecision(updated, actual);
    }

    // Проверяет регистрацию нового чата.
    @Test
    void givenNewChatWhenRegisteredThenChatExists() {
        assertTrue(repository.registerChat(4L));
        assertTrue(repository.chatExists(4L));
    }

    // Проверяет, что повторная регистрация существующего чата не создает дубль.
    @Test
    void givenExistingChatWhenRegisteredAgainThenFalseIsReturned() {
        assertTrue(repository.registerChat(4L));
        assertFalse(repository.registerChat(4L));
    }

    // Проверяет удаление существующего чата.
    @Test
    void givenExistingChatWhenDeletedThenChatDoesNotExist() {
        assertTrue(repository.registerChat(4L));
        assertAll(
                () -> assertTrue(repository.chatExists(4L)),
                () -> assertTrue(repository.deleteChat(4L)),
                () -> assertFalse(repository.chatExists(4L)));
    }

    // Проверяет добавление нового тега к существующей ссылке.
    @Test
    void givenExistingTrackedLinkWhenAddingNewTagThenTagIsAdded() {
        assertTrue(repository.registerChat(5L));
        repository.save(
                5L, "https://example.com/post/1", List.of("initial"), List.of(), Instant.parse("2026-03-01T00:00:00Z"));

        assertTrue(repository.addTag(5L, "https://example.com/post/1", "urgent"));
        assertEquals(List.of("initial", "urgent"), repository.findTagsByChatIdAndUrl(5L, "https://example.com/post/1"));
    }

    // Проверяет защиту от добавления дублирующего тега.
    @Test
    void givenExistingTrackedLinkWhenAddingDuplicateTagThenFalseIsReturned() {
        assertTrue(repository.registerChat(5L));
        repository.save(
                5L, "https://example.com/post/1", List.of("initial"), List.of(), Instant.parse("2026-03-01T00:00:00Z"));
        assertTrue(repository.addTag(5L, "https://example.com/post/1", "urgent"));
        assertFalse(repository.addTag(5L, "https://example.com/post/1", "urgent"));
    }

    // Проверяет изменение существующего тега.
    @Test
    void givenExistingTrackedLinkWhenUpdatingExistingTagThenTagIsUpdated() {
        assertTrue(repository.registerChat(5L));
        repository.save(
                5L, "https://example.com/post/1", List.of("initial"), List.of(), Instant.parse("2026-03-01T00:00:00Z"));
        assertTrue(repository.addTag(5L, "https://example.com/post/1", "urgent"));

        assertTrue(repository.updateTag(5L, "https://example.com/post/1", "urgent", "high-priority"));
        assertEquals(
                List.of("high-priority", "initial"),
                repository.findTagsByChatIdAndUrl(5L, "https://example.com/post/1"));
    }

    // Проверяет поведение при обновлении отсутствующего тега.
    @Test
    void givenExistingTrackedLinkWhenUpdatingUnknownTagThenFalseIsReturned() {
        assertTrue(repository.registerChat(5L));
        repository.save(
                5L, "https://example.com/post/1", List.of("initial"), List.of(), Instant.parse("2026-03-01T00:00:00Z"));

        assertFalse(repository.updateTag(5L, "https://example.com/post/1", "unknown", "new-tag"));
    }

    // Проверяет удаление существующего тега и неизменность остальных полей подписки.
    @Test
    void givenExistingTrackedLinkWhenDeletingExistingTagThenTagIsRemoved() {
        assertTrue(repository.registerChat(5L));
        TrackedLink saved = repository.save(
                5L, "https://example.com/post/1", List.of("initial"), List.of(), Instant.parse("2026-03-01T00:00:00Z"));
        assertTrue(repository.addTag(5L, "https://example.com/post/1", "urgent"));
        assertTrue(repository.deleteTag(5L, "https://example.com/post/1", "urgent"));
        List<TrackedLink> links = repository.findAllByChatId(5L);
        assertAll(
                () -> assertEquals(
                        List.of("initial"), repository.findTagsByChatIdAndUrl(5L, "https://example.com/post/1")),
                () -> assertEquals(1, links.size()),
                () -> assertTrackedLinkEqualsIgnoringDbTimePrecision(saved, links.getFirst()));
    }

    // Проверяет, что повторное удаление отсутствующего тега возвращает false.
    @Test
    void givenExistingTrackedLinkWhenDeletingMissingTagThenFalseIsReturned() {
        assertTrue(repository.registerChat(5L));
        repository.save(
                5L, "https://example.com/post/1", List.of("initial"), List.of(), Instant.parse("2026-03-01T00:00:00Z"));
        assertFalse(repository.deleteTag(5L, "https://example.com/post/1", "high-priority"));
    }

    // Проверяет, что несуществующий чат не найден в хранилище.
    @Test
    void givenNonExistingChatWhenCheckingExistsThenFalseIsReturned() {
        assertFalse(repository.chatExists(100L));
    }

    // Проверяет, что удаление несуществующего чата возвращает false.
    @Test
    void givenNonExistingChatWhenDeletingThenFalseIsReturned() {
        assertFalse(repository.deleteChat(100L));
    }

    // Проверяет, что поиск несуществующей ссылки возвращает пустой результат.
    @Test
    void givenNonExistingChatAndUrlWhenFindingTrackedLinkThenEmptyOptionalIsReturned() {
        assertTrue(
                repository.findByChatIdAndUrl(100L, "https://missing.example").isEmpty());
    }

    // Проверяет, что удаление несуществующей ссылки возвращает false.
    @Test
    void givenNonExistingChatAndUrlWhenDeletingTrackedLinkThenFalseIsReturned() {
        assertFalse(repository.deleteByChatIdAndUrl(100L, "https://missing.example"));
    }

    // Проверяет, что список ссылок для отсутствующего чата пуст.
    @Test
    void givenNonExistingChatWhenListingLinksThenEmptyListIsReturned() {
        assertEquals(List.of(), repository.findAllByChatId(100L));
    }

    // Проверяет, что список тегов для отсутствующей подписки пуст.
    @Test
    void givenNonExistingChatAndUrlWhenListingTagsThenEmptyListIsReturned() {
        assertEquals(List.of(), repository.findTagsByChatIdAndUrl(100L, "https://missing.example"));
    }

    // Проверяет, что добавление тега к отсутствующей подписке возвращает false.
    @Test
    void givenNonExistingLinkWhenAddingTagThenFalseIsReturned() {
        assertTrue(repository.registerChat(6L));
        assertFalse(repository.addTag(6L, "https://missing.example", "tag"));
    }

    // Проверяет, что обновление тега у отсутствующей подписки возвращает false.
    @Test
    void givenNonExistingLinkWhenUpdatingTagThenFalseIsReturned() {
        assertTrue(repository.registerChat(6L));
        assertFalse(repository.updateTag(6L, "https://missing.example", "old", "new"));
    }

    // Проверяет, что удаление тега у отсутствующей подписки возвращает false.
    @Test
    void givenNonExistingLinkWhenDeletingTagThenFalseIsReturned() {
        assertTrue(repository.registerChat(6L));
        assertFalse(repository.deleteTag(6L, "https://missing.example", "tag"));
    }

    // Проверяет, что список тегов у отсутствующей подписки пуст.
    @Test
    void givenNonExistingLinkWhenListingTagsThenEmptyListIsReturned() {
        assertTrue(repository.registerChat(6L));
        assertEquals(List.of(), repository.findTagsByChatIdAndUrl(6L, "https://missing.example"));
    }

    // Проверяет граничные значения paging для выборки ссылок чата.
    @ParameterizedTest(name = "Given chat links paging limit={0} offset={1} When querying Then empty page is returned")
    @MethodSource("invalidPagingCases")
    void givenInvalidPagingForChatLinksWhenQueryingThenEmptyPageIsReturned(int limit, int offset) {
        assertEquals(List.of(), repository.findAllByChatId(1L, limit, offset));
    }

    // Проверяет граничные значения paging для глобальной выборки ссылок.
    @ParameterizedTest(
            name = "Given tracked links paging limit={0} offset={1} When querying Then empty page is returned")
    @MethodSource("invalidPagingCases")
    void givenInvalidPagingForTrackedLinksWhenQueryingThenEmptyPageIsReturned(int limit, int offset) {
        assertEquals(List.of(), repository.findAllTrackedLinks(limit, offset));
    }

    // Проверяет корректность постраничной выборки ссылок внутри чата.
    @Test
    void givenMultipleLinksWhenPaginatingByChatThenWindowIsReturnedInInsertOrder() {
        assertTrue(repository.registerChat(7L));
        TrackedLink first = repository.save(
                7L, "https://example.com/a", List.of(), List.of(), Instant.parse("2026-03-01T00:00:00Z"));
        TrackedLink second = repository.save(
                7L, "https://example.com/b", List.of(), List.of(), Instant.parse("2026-03-01T00:00:00Z"));
        TrackedLink third = repository.save(
                7L, "https://example.com/c", List.of(), List.of(), Instant.parse("2026-03-01T00:00:00Z"));

        List<TrackedLink> page1 = repository.findAllByChatId(7L, 2, 0);
        List<TrackedLink> page2 = repository.findAllByChatId(7L, 2, 2);
        List<TrackedLink> page3 = repository.findAllByChatId(7L, 2, 4);

        assertAll(
                () -> assertTrackedLinkListsEqualIgnoringDbTimePrecision(List.of(first, second), page1),
                () -> assertTrackedLinkListsEqualIgnoringDbTimePrecision(List.of(third), page2),
                () -> assertEquals(List.of(), page3));
    }

    // Проверяет корректность постраничной выборки ссылок по всем чатам.
    @Test
    void givenMultipleChatsWhenPaginatingTrackedLinksThenCrossChatWindowIsReturned() {
        assertTrue(repository.registerChat(8L));
        assertTrue(repository.registerChat(9L));
        TrackedLink first = repository.save(
                8L, "https://example.com/1", List.of(), List.of(), Instant.parse("2026-03-01T00:00:00Z"));
        TrackedLink second = repository.save(
                9L, "https://example.com/2", List.of(), List.of(), Instant.parse("2026-03-01T00:00:00Z"));
        TrackedLink third = repository.save(
                8L, "https://example.com/3", List.of(), List.of(), Instant.parse("2026-03-01T00:00:00Z"));

        List<TrackedLink> firstTwo = repository.findAllTrackedLinks(2, 0);
        List<TrackedLink> nextTwo = repository.findAllTrackedLinks(2, 2);

        assertAll(
                () -> assertTrackedLinkListsEqualIgnoringDbTimePrecision(List.of(first, second), firstTwo),
                () -> assertTrackedLinkListsEqualIgnoringDbTimePrecision(List.of(third), nextTwo));
    }

    private static Stream<Arguments> invalidPagingCases() {
        return Stream.of(Arguments.of(0, 0), Arguments.of(-1, 0), Arguments.of(1, -1));
    }

    private void assertTrackedLinkEqualsIgnoringDbTimePrecision(TrackedLink expected, TrackedLink actual) {
        assertAll(
                () -> assertEquals(expected.id(), actual.id()),
                () -> assertEquals(expected.chatId(), actual.chatId()),
                () -> assertEquals(expected.url(), actual.url()),
                () -> assertEquals(expected.tags(), actual.tags()),
                () -> assertEquals(expected.filters(), actual.filters()),
                () -> assertInstantEqualWithMicrosecondTolerance(expected.lastCheckedAt(), actual.lastCheckedAt()),
                () -> assertInstantEqualWithMicrosecondTolerance(expected.lastUpdatedAt(), actual.lastUpdatedAt()));
    }

    private void assertInstantEqualWithMicrosecondTolerance(Instant expected, Instant actual) {
        long differenceNanos =
                Math.abs(java.time.Duration.between(expected, actual).toNanos());
        assertTrue(
                differenceNanos <= 1_000,
                "Expected and actual instants differ more than 1 microsecond: expected=%s actual=%s"
                        .formatted(expected, actual));
    }

    private void assertTrackedLinkListsEqualIgnoringDbTimePrecision(
            List<TrackedLink> expected, List<TrackedLink> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertTrackedLinkEqualsIgnoringDbTimePrecision(expected.get(index), actual.get(index));
        }
    }
}
