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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class LinkUpdatesSchedulerTest {

    @Test
    void givenUpdatedLinkWhenSchedulerRunsThenOnlySubscribersReceiveNotification() {
        // Проверяет базовый сценарий: обновление ссылки рассылается только подписанному чату.
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
        LinkUpdatePersistenceService persistenceService =
                new LinkUpdatePersistenceService(messageSender, trackedLinkMaintenanceService);
        LinkUpdateChecker linkUpdateChecker =
                new LinkUpdateChecker(linkMetadataResolver, persistenceService, trackedLinkMaintenanceService);
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
        // Проверяет, что без обновления ссылки планировщик не отправляет уведомление.
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
        LinkUpdatePersistenceService persistenceService =
                new LinkUpdatePersistenceService(messageSender, trackedLinkMaintenanceService);
        LinkUpdateChecker linkUpdateChecker =
                new LinkUpdateChecker(linkMetadataResolver, persistenceService, trackedLinkMaintenanceService);
        SchedulerRunReporter runReporter = new SchedulerRunReporter(messageSender);
        LinkUpdatesScheduler scheduler = new LinkUpdatesScheduler(
                trackedLinkMaintenanceService, linkUpdateChecker, runReporter, schedulerProperties);

        scheduler.checkUpdates();

        assertEquals(List.of(), messageSender.sentUpdates);
    }

    @Test
    void givenFailingLinkWhenSchedulerRunsThenOtherLinksStillProcessed() {
        // Проверяет изоляцию ошибки одной ссылки и публикацию системного error-отчета.
        var responsesByRepo = new java.util.concurrent.atomic.AtomicReference<>(Map.<String, Supplier<Instant>>of(
                "good-repo", () -> Instant.parse("2026-02-25T00:00:00Z"),
                "bad-repo", () -> Instant.parse("2026-02-25T00:00:00Z")));
        Map<String, Supplier<Instant>> failureResponses = Map.<String, Supplier<Instant>>of(
                "good-repo", () -> Instant.parse("2026-02-25T00:00:00Z"),
                "bad-repo",
                        () -> {
                            throw new RuntimeException("API error");
                        });
        GithubClient githubClient = (owner, repo) -> responsesByRepo
                .get()
                .getOrDefault(repo, () -> Instant.parse("2026-02-25T00:00:00Z"))
                .get();
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
        responsesByRepo.set(failureResponses);

        CapturingMessageSender messageSender = new CapturingMessageSender();
        LinkUpdatePersistenceService persistenceService =
                new LinkUpdatePersistenceService(messageSender, trackedLinkMaintenanceService);
        LinkUpdateChecker linkUpdateChecker =
                new LinkUpdateChecker(linkMetadataResolver, persistenceService, trackedLinkMaintenanceService);
        SchedulerRunReporter runReporter = new SchedulerRunReporter(messageSender);
        LinkUpdatesScheduler scheduler = new LinkUpdatesScheduler(
                trackedLinkMaintenanceService, linkUpdateChecker, runReporter, schedulerProperties);

        scheduler.checkUpdates();

        List<LinkUpdate> errorReports = messageSender.sentUpdates.stream()
                .filter(u -> u.url().startsWith("system://"))
                .toList();

        assertEquals(1, errorReports.size());
        LinkUpdate errorReport = errorReports.getFirst();
        assertEquals("system://scheduler-error-report", errorReport.url());
        assertEquals(List.of(42L), errorReport.tgChatIds());

        String actualDescription = errorReport.description();
        var runIdMatcher =
                Pattern.compile("⚠ Отчёт планировщика \\(runId: ([^)]+)\\)\n").matcher(actualDescription);
        assertTrue(runIdMatcher.find());

        String expectedDescription = """
                ⚠ Отчёт планировщика (runId: %s)
                Обработано: 2, успешно: 1, с ошибками: 1

                Неуспешные ссылки:
                • https://github.com/user/bad-repo — API error
                """.formatted(runIdMatcher.group(1));
        assertEquals(expectedDescription, actualDescription);
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
