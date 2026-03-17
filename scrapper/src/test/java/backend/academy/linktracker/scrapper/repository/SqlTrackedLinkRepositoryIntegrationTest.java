package backend.academy.linktracker.scrapper.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.sql.SqlTrackedLinkRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {ScrapperApplication.class, TestcontainersConfiguration.class})
@TestPropertySource(properties = {"app.database.access-type=SQL", "app.scheduler.interval=365d"})
class SqlTrackedLinkRepositoryIntegrationTest {

    @Autowired
    private TrackedLinkRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from chats");
    }

    @Test
    void givenSqlAccessTypeWhenContextStartsThenSqlImplementationIsUsed() {
        assertInstanceOf(SqlTrackedLinkRepository.class, repository);
    }

    @Test
    void givenTrackedLinkWhenSavedAndReadThenCrudWorks() {
        assertTrue(repository.registerChat(1L));

        TrackedLink saved = repository.save(
                1L,
                "https://github.com/user/repo",
                List.of("work"),
                List.of("lang=java"),
                Instant.parse("2026-03-01T00:00:00Z"));

        List<TrackedLink> links = repository.findAllByChatId(1L);
        assertEquals(1, links.size());
        assertEquals(saved.url(), links.getFirst().url());
        assertEquals(List.of("work"), links.getFirst().tags());

        assertTrue(repository.deleteByChatIdAndUrl(1L, saved.url()));
        assertEquals(List.of(), repository.findAllByChatId(1L));
    }

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

    @Test
    void givenChatLifecycleWhenRegisterAndDeleteThenStateIsPersisted() {
        assertTrue(repository.registerChat(3L));
        assertFalse(repository.registerChat(3L));
        assertTrue(repository.chatExists(3L));
        assertTrue(repository.deleteChat(3L));
        assertFalse(repository.chatExists(3L));
    }
}
