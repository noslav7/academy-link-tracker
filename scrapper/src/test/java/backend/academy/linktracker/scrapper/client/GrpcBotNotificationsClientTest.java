package backend.academy.linktracker.scrapper.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.grpc.BotUpdatesApiGrpc;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class GrpcBotNotificationsClientTest {

    private final AtomicReference<LinkUpdateRequest> capturedRequest = new AtomicReference<>();
    private Server server;
    private io.grpc.ManagedChannel channel;
    private GrpcBotNotificationsClient client;

    @BeforeEach
    void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new BotUpdatesApiGrpc.BotUpdatesApiImplBase() {
                    @Override
                    public void sendUpdate(LinkUpdateRequest request, StreamObserver<EmptyMessage> responseObserver) {
                        capturedRequest.set(request);
                        responseObserver.onNext(EmptyMessage.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new GrpcBotNotificationsClient(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void givenLinkUpdateWhenSendUpdateCalledThenGrpcRequestContainsExactPayload() {
        // Проверяет, что клиент отправляет в gRPC полный payload без потери полей.
        client.sendUpdate(new LinkUpdate(7L, "https://example.com", "updated", List.of(101L, 202L)));
        LinkUpdateRequest request = capturedRequest.get();

        assertAll(
                () -> assertEquals(7L, request.getId()),
                () -> assertEquals("https://example.com", request.getUrl()),
                () -> assertEquals("updated", request.getDescription()),
                () -> assertEquals(List.of(101L, 202L), request.getTgChatIdsList()));
    }
}
