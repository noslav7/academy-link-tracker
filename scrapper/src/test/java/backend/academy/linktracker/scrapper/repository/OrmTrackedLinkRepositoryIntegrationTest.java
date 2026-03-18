package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.repository.orm.OrmTrackedLinkRepository;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

@Tag("integration")
@TestPropertySource(properties = {"app.database.access-type=ORM", "app.scheduler.interval=365d"})
class OrmTrackedLinkRepositoryIntegrationTest extends AbstractTrackedLinkRepositoryIntegrationTest {

    @Override
    protected Class<?> expectedRepositoryType() {
        return OrmTrackedLinkRepository.class;
    }
}
