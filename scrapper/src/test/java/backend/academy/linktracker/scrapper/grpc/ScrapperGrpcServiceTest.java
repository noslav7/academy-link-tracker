package backend.academy.linktracker.scrapper.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatIdRequest;
import backend.academy.linktracker.grpc.LinkResponse;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.ScrapperApiGrpc;
import backend.academy.linktracker.scrapper.client.GithubClient;
import backend.academy.linktracker.scrapper.client.StackoverflowClient;
import backend.academy.linktracker.scrapper.repository.InMemoryTrackedLinkRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import backend.academy.linktracker.scrapper.service.ChatLinkAccessValidator;
import backend.academy.linktracker.scrapper.service.ChatService;
import backend.academy.linktracker.scrapper.service.GithubLinkMetadataResolver;
import backend.academy.linktracker.scrapper.service.LinkFormatMetadataResolverFactory;
import backend.academy.linktracker.scrapper.service.LinkMetadataResolver;
import backend.academy.linktracker.scrapper.service.LinkService;
import backend.academy.linktracker.scrapper.service.StackoverflowLinkMetadataResolver;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ScrapperGrpcServiceTest {

    private Server server;
    private io.grpc.ManagedChannel channel;
    private ScrapperApiGrpc.ScrapperApiBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        GithubClient githubClient = (owner, repository) -> Instant.parse("2026-02-25T00:00:00Z");
        StackoverflowClient stackoverflowClient = questionId -> Instant.parse("2026-02-25T00:00:00Z");
        TrackedLinkRepository repository = new InMemoryTrackedLinkRepository();
        LinkMetadataResolver linkMetadataResolver =
                new LinkMetadataResolver(new LinkFormatMetadataResolverFactory(List.of(
                        new GithubLinkMetadataResolver(githubClient),
                        new StackoverflowLinkMetadataResolver(stackoverflowClient))));
        LinkService linkService =
                new LinkService(repository, linkMetadataResolver, new ChatLinkAccessValidator(repository));
        ChatService chatService = new ChatService(repository);
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new ScrapperGrpcService(chatService, linkService))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
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
    void givenRegisteredChatAndAddedLinkWhenListLinksCalledThenReturnsExactGrpcResponse() {
        // Проверяет базовый gRPC-контракт: регистрация чата, добавление ссылки и полный ответ listLinks.
        stub.registerChat(ChatIdRequest.newBuilder().setChatId(42L).build());
        stub.addLink(AddLinkRequest.newBuilder()
                .setChatId(42L)
                .setLink("https://github.com/octocat/Hello-World")
                .build());
        ListLinksResponse response =
                stub.listLinks(ChatIdRequest.newBuilder().setChatId(42L).build());
        ListLinksResponse expected = ListLinksResponse.newBuilder()
                .addLinks(LinkResponse.newBuilder()
                        .setId(1L)
                        .setUrl("https://github.com/octocat/Hello-World")
                        .build())
                .setSize(1)
                .build();
        assertEquals(expected, response);
    }

    @Test
    void givenAlreadyRegisteredChatWhenRegisterCalledAgainThenReturnsAlreadyExists() {
        // Проверяет, что повторная регистрация чата возвращает gRPC-ошибку ALREADY_EXISTS.
        stub.registerChat(ChatIdRequest.newBuilder().setChatId(42L).build());

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> stub.registerChat(
                        ChatIdRequest.newBuilder().setChatId(42L).build()));

        assertEquals(Status.ALREADY_EXISTS.getCode(), exception.getStatus().getCode());
    }

    @Test
    void givenMissingChatWhenAddLinkCalledThenReturnsNotFound() {
        // Проверяет, что добавление ссылки для несуществующего чата возвращает NOT_FOUND.
        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> stub.addLink(AddLinkRequest.newBuilder()
                        .setChatId(42L)
                        .setLink("https://github.com/octocat/Hello-World")
                        .build()));

        assertEquals(Status.NOT_FOUND.getCode(), exception.getStatus().getCode());
    }

    @Test
    void givenBlankLinkWhenAddLinkCalledThenReturnsInvalidArgument() {
        // Проверяет валидацию ссылки: пустое значение должно давать INVALID_ARGUMENT.
        stub.registerChat(ChatIdRequest.newBuilder().setChatId(42L).build());

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> stub.addLink(
                        AddLinkRequest.newBuilder().setChatId(42L).setLink(" ").build()));

        assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode());
    }
}
