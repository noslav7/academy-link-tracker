package backend.academy.linktracker.bot.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatIdRequest;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.ScrapperApiGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
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
        server = ServerBuilder.forPort(0).addService(new FakeScrapperService()).build().start();
        channel = io.grpc.ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        client = new GrpcScrapperClient(channel);
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
    void shouldListLinksOverGrpc() {
        List<LinkResponse> links = client.getLinks(42L);
        assertEquals(1, links.size());
        assertEquals("https://github.com/octocat/Hello-World", links.getFirst().url());
    }

    @Test
    void shouldMapAlreadyExistsToDomainException() {
        assertThrows(LinkAlreadyTrackedException.class, () -> client.addLink(42L, "https://duplicate", List.of("tag")));
    }

    private static final class FakeScrapperService extends ScrapperApiGrpc.ScrapperApiImplBase {

        @Override
        public void registerChat(ChatIdRequest request, StreamObserver<EmptyMessage> responseObserver) {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }

        @Override
        public void addLink(AddLinkRequest request, StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
            if ("https://duplicate".equals(request.getLink())) {
                responseObserver.onError(Status.ALREADY_EXISTS.asRuntimeException());
                return;
            }
            responseObserver.onNext(backend.academy.linktracker.grpc.LinkResponse.newBuilder()
                    .setId(1L)
                    .setUrl(request.getLink())
                    .addAllTags(request.getTagsList())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void removeLink(
                RemoveLinkRequest request, StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
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
}
