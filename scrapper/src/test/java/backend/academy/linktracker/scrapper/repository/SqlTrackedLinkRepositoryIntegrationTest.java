package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.repository.sql.SqlTrackedLinkRepository;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

@Tag("integration")
@TestPropertySource(properties = {"app.database.access-type=SQL", "app.scheduler.interval=365d"})
class SqlTrackedLinkRepositoryIntegrationTest extends AbstractTrackedLinkRepositoryIntegrationTest {

    @Override
    protected Class<?> expectedRepositoryType() {
        return SqlTrackedLinkRepository.class;
    }
}
