package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LinkUpdatesSchedulerTest {

    @Test
    void givenUpdatedLinkWhenSchedulerRunsThenOnlySubscribersReceiveNotification() {
        // Проверяет, что при обновлении ссылки уведомление уходит только подписанному чату.
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
        TrackedLinkMaintenanceService trackedLinkMaintenanceService = new TrackedLinkMaintenanceService(repository);
        ChatService chatService = new ChatService(repository);
        chatService.register(42L);
        chatService.register(77L);
        linkService.add(42L, new AddLinkRequest("https://github.com/user/repo", List.of("work"), List.of()));

        CapturingBotNotificationsClient notificationsClient = new CapturingBotNotificationsClient();
        SchedulerProperties schedulerProperties = new SchedulerProperties();
        schedulerProperties.setInterval(Duration.ofSeconds(30));
        LinkUpdatesScheduler scheduler = new LinkUpdatesScheduler(
                trackedLinkMaintenanceService, linkMetadataResolver, notificationsClient, schedulerProperties);

        githubUpdatedAt.set(Instant.parse("2026-02-26T00:00:00Z"));
        scheduler.checkUpdates();

        assertEquals(1, notificationsClient.sentUpdates.size());
        assertEquals(List.of(42L), notificationsClient.sentUpdates.getFirst().tgChatIds());
    }

    @Test
    void givenLinkWasNotUpdatedWhenSchedulerRunsThenNoNotificationIsSent() {
        // Проверяет, что при отсутствии обновлений scheduler не отправляет уведомления.
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
        TrackedLinkMaintenanceService trackedLinkMaintenanceService = new TrackedLinkMaintenanceService(repository);
        ChatService chatService = new ChatService(repository);
        chatService.register(42L);
        linkService.add(42L, new AddLinkRequest("https://github.com/user/repo", List.of("work"), List.of()));

        CapturingBotNotificationsClient notificationsClient = new CapturingBotNotificationsClient();
        SchedulerProperties schedulerProperties = new SchedulerProperties();
        schedulerProperties.setInterval(Duration.ofSeconds(30));
        LinkUpdatesScheduler scheduler = new LinkUpdatesScheduler(
                trackedLinkMaintenanceService, linkMetadataResolver, notificationsClient, schedulerProperties);

        scheduler.checkUpdates();

        assertEquals(List.of(), notificationsClient.sentUpdates);
    }

    private static final class CapturingBotNotificationsClient implements BotNotificationsClient {
        private final List<LinkUpdate> sentUpdates = new ArrayList<>();

        @Override
        public void sendUpdate(LinkUpdate update) {
            sentUpdates.add(update);
        }
    }
}
