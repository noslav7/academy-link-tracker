package backend.academy.linktracker.bot.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.UpdateNotificationService;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotUpdatesGrpcServiceTest {

    @Test
    void givenGrpcLinkUpdateRequestWhenSendUpdateCalledThenDelegatesExactMappedPayloadToNotificationService() {
        // Проверяет канал gRPC: запрос маппится в domain DTO без потерь.
        CapturingUpdateNotificationService notificationService = new CapturingUpdateNotificationService();
        BotUpdatesGrpcService grpcService = new BotUpdatesGrpcService(notificationService);
        CapturingStreamObserver observer = new CapturingStreamObserver();
        LinkUpdateRequest request = LinkUpdateRequest.newBuilder()
                .setId(10L)
                .setUrl("https://github.com/octocat/hello-world")
                .setDescription("updated")
                .addTgChatIds(1001L)
                .addTgChatIds(1002L)
                .build();

        grpcService.sendUpdate(request, observer);

        assertEquals(
                new LinkUpdate(10L, "https://github.com/octocat/hello-world", "updated", List.of(1001L, 1002L)),
                notificationService.capturedUpdate);
    }

    @Test
    void givenGrpcLinkUpdateRequestWhenSendUpdateCalledThenReturnsSingleEmptyResponseAndCompletes() {
        // Проверяет канал gRPC: сервис отправляет пустой ответ и завершает stream.
        CapturingUpdateNotificationService notificationService = new CapturingUpdateNotificationService();
        BotUpdatesGrpcService grpcService = new BotUpdatesGrpcService(notificationService);
        CapturingStreamObserver observer = new CapturingStreamObserver();
        LinkUpdateRequest request = LinkUpdateRequest.newBuilder()
                .setId(11L)
                .setUrl("https://stackoverflow.com/questions/123/example")
                .setDescription("updated")
                .addTgChatIds(42L)
                .build();

        grpcService.sendUpdate(request, observer);

        assertAll(
                () -> assertEquals(1, observer.onNextMessages.size()),
                () -> assertEquals(0, observer.errorCount),
                () -> assertEquals(1, observer.completedCount));
    }

    private static final class CapturingUpdateNotificationService extends UpdateNotificationService {
        private LinkUpdate capturedUpdate;

        private CapturingUpdateNotificationService() {
            super(null);
        }

        @Override
        public void notifyAboutUpdate(LinkUpdate update) {
            capturedUpdate = update;
        }
    }

    private static final class CapturingStreamObserver implements StreamObserver<EmptyMessage> {
        private final List<EmptyMessage> onNextMessages = new ArrayList<>();
        private int errorCount;
        private int completedCount;

        @Override
        public void onNext(EmptyMessage value) {
            onNextMessages.add(value);
        }

        @Override
        public void onError(Throwable t) {
            errorCount++;
        }

        @Override
        public void onCompleted() {
            completedCount++;
        }
    }
}
