package backend.academy.linktracker.scrapper.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.orm.OrmTrackedLinkRepository;
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
@TestPropertySource(properties = {"app.database.access-type=ORM", "app.scheduler.interval=365d"})
class OrmTrackedLinkRepositoryIntegrationTest {

    @Autowired
    private TrackedLinkRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from chats");
    }

    @Test
    void givenOrmAccessTypeWhenContextStartsThenOrmImplementationIsUsed() {
        assertInstanceOf(OrmTrackedLinkRepository.class, repository);
    }

    @Test
    void givenTrackedLinkWhenSavedAndReadThenCrudWorks() {
        assertTrue(repository.registerChat(11L));

        TrackedLink saved = repository.save(
                11L,
                "https://stackoverflow.com/questions/1",
                List.of("qna"),
                List.of("score>0"),
                Instant.parse("2026-03-01T00:00:00Z"));

        List<TrackedLink> links = repository.findAllByChatId(11L);
        assertEquals(1, links.size());
        assertEquals(saved.url(), links.getFirst().url());
        assertEquals(List.of("qna"), links.getFirst().tags());

        assertTrue(repository.deleteByChatIdAndUrl(11L, saved.url()));
        assertEquals(List.of(), repository.findAllByChatId(11L));
    }

    @Test
    void givenDuplicateLinkWhenSavedTwiceThenErrorIsThrown() {
        assertTrue(repository.registerChat(12L));
        repository.save(
                12L,
                "https://stackoverflow.com/questions/1",
                List.of(),
                List.of(),
                Instant.parse("2026-03-01T00:00:00Z"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.save(
                        12L,
                        "https://stackoverflow.com/questions/1",
                        List.of(),
                        List.of(),
                        Instant.parse("2026-03-01T00:00:00Z")));
    }

    @Test
    void givenChatLifecycleWhenRegisterAndDeleteThenStateIsPersisted() {
        assertTrue(repository.registerChat(13L));
        assertFalse(repository.registerChat(13L));
        assertTrue(repository.chatExists(13L));
        assertTrue(repository.deleteChat(13L));
        assertFalse(repository.chatExists(13L));
    }
}
