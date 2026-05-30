package backend.academy.linktracker.bot.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatIdRequest;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.ScrapperApiGrpc;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrpcScrapperClientTest {

    private Server server;
    private io.grpc.ManagedChannel channel;
    private GrpcScrapperClient client;

    @BeforeEach
    void setUp() throws IOException {
        startClientWithService(new ListLinksScrapperService());
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
    void givenGrpcListLinksResponseWhenGetLinksCalledThenReturnsMappedDomainLinks() {
        // Проверяет, что gRPC-ответ со списком ссылок корректно маппится в доменную модель клиента.
        List<backend.academy.linktracker.bot.client.dto.LinkResponse> links = client.getLinks(42L);

        assertEquals(
                List.of(new backend.academy.linktracker.bot.client.dto.LinkResponse(
                        1L, "https://github.com/octocat/Hello-World", List.of("work"), List.of())),
                links);
    }

    @Test
    void givenGrpcAlreadyExistsErrorWhenAddLinkCalledThenThrowsDomainDuplicateException() {
        // Проверяет, что ошибка ALREADY_EXISTS из gRPC конвертируется в LinkAlreadyTrackedException.
        reconfigureClient(new DuplicateLinkScrapperService());
        assertThrows(
                LinkAlreadyTrackedException.class,
                () -> client.addLink(42L, "https://duplicate", List.of("tag"), List.of("author=me")));
    }

    private void reconfigureClient(ScrapperApiGrpc.ScrapperApiImplBase service) {
        tearDown();
        try {
            startClientWithService(service);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to reconfigure in-process gRPC client", exception);
        }
    }

    private void startClientWithService(ScrapperApiGrpc.ScrapperApiImplBase service) throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new GrpcScrapperClient(channel);
    }

    private static final class ListLinksScrapperService extends ScrapperApiGrpc.ScrapperApiImplBase {

        @Override
        public void registerChat(ChatIdRequest request, StreamObserver<EmptyMessage> responseObserver) {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }

        @Override
        public void addLink(
                AddLinkRequest request,
                StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
            responseObserver.onNext(backend.academy.linktracker.grpc.LinkResponse.newBuilder()
                    .setId(1L)
                    .setUrl(request.getLink())
                    .addAllTags(request.getTagsList())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void removeLink(
                RemoveLinkRequest request,
                StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
            responseObserver.onNext(backend.academy.linktracker.grpc.LinkResponse.newBuilder()
                    .setId(1L)
                    .setUrl(request.getLink())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void listLinks(ChatIdRequest request, StreamObserver<ListLinksResponse> responseObserver) {
            responseObserver.onNext(ListLinksResponse.newBuilder()
                    .addLinks(backend.academy.linktracker.grpc.LinkResponse.newBuilder()
                            .setId(1L)
                            .setUrl("https://github.com/octocat/Hello-World")
                            .addTags("work")
                            .build())
                    .setSize(1)
                    .build());
            responseObserver.onCompleted();
        }
    }

    private static final class DuplicateLinkScrapperService extends ScrapperApiGrpc.ScrapperApiImplBase {
        @Override
        public void addLink(
                AddLinkRequest request,
                StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
            responseObserver.onError(Status.ALREADY_EXISTS.asRuntimeException());
        }
    }
}
