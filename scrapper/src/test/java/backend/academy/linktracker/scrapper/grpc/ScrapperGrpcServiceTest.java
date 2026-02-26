package backend.academy.linktracker.scrapper.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatIdRequest;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.ScrapperApiGrpc;
import backend.academy.linktracker.scrapper.client.GithubClient;
import backend.academy.linktracker.scrapper.client.StackoverflowClient;
import backend.academy.linktracker.scrapper.repository.InMemoryTrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import backend.academy.linktracker.scrapper.service.ChatService;
import backend.academy.linktracker.scrapper.service.LinkMetadataResolver;
import backend.academy.linktracker.scrapper.service.LinkService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScrapperGrpcServiceTest {

    private Server server;
    private io.grpc.ManagedChannel channel;
    private ScrapperApiGrpc.ScrapperApiBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        GithubClient githubClient = (owner, repository) -> Instant.parse("2026-02-25T00:00:00Z");
        StackoverflowClient stackoverflowClient = questionId -> Instant.parse("2026-02-25T00:00:00Z");
        TrackedLinkRepository repository = new InMemoryTrackedLinkRepository();
        LinkService linkService =
                new LinkService(repository, new LinkMetadataResolver(githubClient, stackoverflowClient));
        ChatService chatService = new ChatService(repository);
        server = ServerBuilder.forPort(0)
                .addService(new ScrapperGrpcService(chatService, linkService))
                .build()
                .start();
        channel = io.grpc.ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        stub = ScrapperApiGrpc.newBlockingStub(channel);
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
    void shouldRegisterAddAndListViaGrpc() {
        stub.registerChat(ChatIdRequest.newBuilder().setChatId(42L).build());
        stub.addLink(AddLinkRequest.newBuilder()
                .setChatId(42L)
                .setLink("https://github.com/octocat/Hello-World")
                .build());
        ListLinksResponse response =
                stub.listLinks(ChatIdRequest.newBuilder().setChatId(42L).build());
        assertEquals(1, response.getSize());
        assertEquals(
                "https://github.com/octocat/Hello-World", response.getLinks(0).getUrl());
    }
}
