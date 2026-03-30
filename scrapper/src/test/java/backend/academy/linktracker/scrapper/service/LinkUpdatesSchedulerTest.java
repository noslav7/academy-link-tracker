package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.client.GithubClient;
import backend.academy.linktracker.scrapper.client.StackoverflowClient;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.InMemoryTrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LinkUpdatesSchedulerTest {

    @Test
    void givenUpdatedLinkWhenSchedulerRunsThenOnlySubscribersReceiveNotification() {
        AtomicReference<Instant> githubUpdatedAt = new AtomicReference<>(Instant.parse("2026-02-25T00:00:00Z"));
        GithubClient githubClient = (owner, repository) -> githubUpdatedAt.get();
        StackoverflowClient stackoverflowClient = questionId -> Instant.EPOCH;

        TrackedLinkRepository repository = new InMemoryTrackedLinkRepository();
        LinkMetadataResolver linkMetadataResolver =
                new LinkMetadataResolver(new LinkFormatMetadataResolverFactory(List.of(
                        new GithubLinkMetadataResolver(githubClient),
                        new StackoverflowLinkMetadataResolver(stackoverflowClient))));
        ChatLinkAccessValidator accessValidator = new ChatLinkAccessValidator(repository);
        LinkService linkService = new LinkService(repository, linkMetadataResolver, accessValidator);
        SchedulerProperties schedulerProperties = createSchedulerProperties();
        TrackedLinkMaintenanceService trackedLinkMaintenanceService =
                new TrackedLinkMaintenanceService(repository, schedulerProperties);
        ChatService chatService = new ChatService(repository);
        chatService.register(42L);
        chatService.register(77L);
        linkService.add(42L, new AddLinkRequest("https://github.com/user/repo", List.of("work"), List.of()));

        CapturingMessageSender messageSender = new CapturingMessageSender();
        LinkUpdateChecker linkUpdateChecker =
                new LinkUpdateChecker(linkMetadataResolver, messageSender, trackedLinkMaintenanceService);
        SchedulerRunReporter runReporter = new SchedulerRunReporter(messageSender);
        LinkUpdatesScheduler scheduler = new LinkUpdatesScheduler(
                trackedLinkMaintenanceService, linkUpdateChecker, runReporter, schedulerProperties);

        githubUpdatedAt.set(Instant.parse("2026-02-26T00:00:00Z"));
        scheduler.checkUpdates();

        assertEquals(1, messageSender.sentUpdates.size());
        assertEquals(List.of(42L), messageSender.sentUpdates.getFirst().tgChatIds());
    }

    @Test
    void givenLinkWasNotUpdatedWhenSchedulerRunsThenNoNotificationIsSent() {
        AtomicReference<Instant> githubUpdatedAt = new AtomicReference<>(Instant.parse("2026-02-25T00:00:00Z"));
        GithubClient githubClient = (owner, repository) -> githubUpdatedAt.get();
        StackoverflowClient stackoverflowClient = questionId -> Instant.EPOCH;

        TrackedLinkRepository repository = new InMemoryTrackedLinkRepository();
        LinkMetadataResolver linkMetadataResolver =
                new LinkMetadataResolver(new LinkFormatMetadataResolverFactory(List.of(
                        new GithubLinkMetadataResolver(githubClient),
                        new StackoverflowLinkMetadataResolver(stackoverflowClient))));
        ChatLinkAccessValidator accessValidator = new ChatLinkAccessValidator(repository);
        LinkService linkService = new LinkService(repository, linkMetadataResolver, accessValidator);
        SchedulerProperties schedulerProperties = createSchedulerProperties();
        TrackedLinkMaintenanceService trackedLinkMaintenanceService =
                new TrackedLinkMaintenanceService(repository, schedulerProperties);
        ChatService chatService = new ChatService(repository);
        chatService.register(42L);
        linkService.add(42L, new AddLinkRequest("https://github.com/user/repo", List.of("work"), List.of()));

        CapturingMessageSender messageSender = new CapturingMessageSender();
        LinkUpdateChecker linkUpdateChecker =
                new LinkUpdateChecker(linkMetadataResolver, messageSender, trackedLinkMaintenanceService);
        SchedulerRunReporter runReporter = new SchedulerRunReporter(messageSender);
        LinkUpdatesScheduler scheduler = new LinkUpdatesScheduler(
                trackedLinkMaintenanceService, linkUpdateChecker, runReporter, schedulerProperties);

        scheduler.checkUpdates();

        assertEquals(List.of(), messageSender.sentUpdates);
    }

    @Test
    void givenFailingLinkWhenSchedulerRunsThenOtherLinksStillProcessed() {
        java.util.concurrent.atomic.AtomicBoolean failOnBadRepo = new java.util.concurrent.atomic.AtomicBoolean(false);
        GithubClient githubClient = (owner, repo) -> {
            if (failOnBadRepo.get() && "bad-repo".equals(repo)) {
                throw new RuntimeException("API error");
            }
            return Instant.parse("2026-02-25T00:00:00Z");
        };
        StackoverflowClient stackoverflowClient = questionId -> Instant.EPOCH;

        TrackedLinkRepository repository = new InMemoryTrackedLinkRepository();
        LinkMetadataResolver linkMetadataResolver =
                new LinkMetadataResolver(new LinkFormatMetadataResolverFactory(List.of(
                        new GithubLinkMetadataResolver(githubClient),
                        new StackoverflowLinkMetadataResolver(stackoverflowClient))));
        ChatLinkAccessValidator accessValidator = new ChatLinkAccessValidator(repository);
        LinkService linkService = new LinkService(repository, linkMetadataResolver, accessValidator);
        SchedulerProperties schedulerProperties = createSchedulerProperties();
        TrackedLinkMaintenanceService trackedLinkMaintenanceService =
                new TrackedLinkMaintenanceService(repository, schedulerProperties);
        ChatService chatService = new ChatService(repository);
        chatService.register(42L);
        linkService.add(42L, new AddLinkRequest("https://github.com/user/good-repo", List.of(), List.of()));
        linkService.add(42L, new AddLinkRequest("https://github.com/user/bad-repo", List.of(), List.of()));

        failOnBadRepo.set(true);

        CapturingMessageSender messageSender = new CapturingMessageSender();
        LinkUpdateChecker linkUpdateChecker =
                new LinkUpdateChecker(linkMetadataResolver, messageSender, trackedLinkMaintenanceService);
        SchedulerRunReporter runReporter = new SchedulerRunReporter(messageSender);
        LinkUpdatesScheduler scheduler = new LinkUpdatesScheduler(
                trackedLinkMaintenanceService, linkUpdateChecker, runReporter, schedulerProperties);

        scheduler.checkUpdates();

        List<LinkUpdate> errorReports = messageSender.sentUpdates.stream()
                .filter(u -> u.url().startsWith("system://"))
                .toList();

        assertEquals(1, errorReports.size());
        assertTrue(errorReports.getFirst().description().contains("bad-repo"));
    }

    private static SchedulerProperties createSchedulerProperties() {
        SchedulerProperties props = new SchedulerProperties();
        props.setInterval(Duration.ofSeconds(30));
        props.setBatchSize(100);
        props.setThreadCount(2);
        return props;
    }

    private static final class CapturingMessageSender implements MessageSender {
        private final List<LinkUpdate> sentUpdates = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void send(LinkUpdate update) {
            sentUpdates.add(update);
        }
    }
}
