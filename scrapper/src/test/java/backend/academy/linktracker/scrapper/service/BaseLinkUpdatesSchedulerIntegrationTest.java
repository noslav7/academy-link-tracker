package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        classes = {ScrapperApplication.class, TestcontainersConfiguration.class},
        properties = {
            "app.github.base-url=${wiremock.server.baseUrl}/github",
            "app.stackoverflow.base-url=${wiremock.server.baseUrl}/so",
            "app.bot.base-url=${wiremock.server.baseUrl}/bot",
            "app.scheduler.interval=365d",
            "app.scheduler.batch-size=2",
            "app.scheduler.thread-count=2",
            "app.outbox.publish-interval=365d"
        })
@EnableWireMock
@Tag("integration")
abstract class BaseLinkUpdatesSchedulerIntegrationTest {

    @Autowired
    protected ChatService chatService;

    @Autowired
    protected LinkService linkService;

    @Autowired
    protected LinkUpdatesScheduler linkUpdatesScheduler;

    @Autowired
    protected TrackedLinkMaintenanceService maintenanceService;

    @Autowired
    protected OutboxPublisher outboxPublisher;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LinkUpdatesSchedulerTestSupport.resetState(jdbcTemplate);
    }

    protected void registerAndTrack(long chatId, String url, List<String> tags, List<String> filters) {
        chatService.register(chatId);
        linkService.add(chatId, new AddLinkRequest(url, tags, filters));
    }
}
