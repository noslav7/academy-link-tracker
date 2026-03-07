package backend.academy.linktracker.scrapper.grpc;

import backend.academy.linktracker.grpc.ChatIdRequest;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.ScrapperApiGrpc;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.exception.ChatAlreadyRegisteredException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.exception.UnsupportedLinkException;
import backend.academy.linktracker.scrapper.service.ChatService;
import backend.academy.linktracker.scrapper.service.LinkService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;

/**
 * gRPC-реализация API scrapper-сервиса.
 */
public class ScrapperGrpcService extends ScrapperApiGrpc.ScrapperApiImplBase {

    /**
     * Сервис регистрации/удаления чатов.
     */
    private final ChatService chatService;
    /**
     * Сервис управления отслеживаемыми ссылками.
     */
    private final LinkService linkService;

    /**
     * Создает gRPC-сервис scrapper.
     *
     * @param chatService сервис чатов
     * @param linkService сервис ссылок
     */
    public ScrapperGrpcService(ChatService chatService, LinkService linkService) {
        this.chatService = chatService;
        this.linkService = linkService;
    }

    /**
     * Регистрирует чат через gRPC API.
     *
     * @param request запрос регистрации чата
     * @param responseObserver observer ответа
     */
    @Override
    public void registerChat(ChatIdRequest request, StreamObserver<EmptyMessage> responseObserver) {
        try {
            chatService.register(request.getChatId());
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        } catch (ChatAlreadyRegisteredException ex) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    /**
     * Добавляет ссылку в отслеживание через gRPC API.
     *
     * @param request gRPC-запрос добавления ссылки
     * @param responseObserver observer ответа
     */
    @Override
    public void addLink(
            backend.academy.linktracker.grpc.AddLinkRequest request,
            StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
        try {
            LinkResponse response = linkService.add(
                    request.getChatId(),
                    new AddLinkRequest(
                            request.getLink(),
                            List.copyOf(request.getTagsList()),
                            List.copyOf(request.getFiltersList())));
            responseObserver.onNext(toGrpcLinkResponse(response));
            responseObserver.onCompleted();
        } catch (LinkAlreadyTrackedException ex) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
        } catch (ChatNotFoundException | LinkNotFoundException ex) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (UnsupportedLinkException | IllegalArgumentException ex) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    /**
     * Удаляет ссылку из отслеживания через gRPC API.
     *
     * @param request gRPC-запрос удаления ссылки
     * @param responseObserver observer ответа
     */
    @Override
    public void removeLink(
            backend.academy.linktracker.grpc.RemoveLinkRequest request,
            StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
        try {
            LinkResponse response = linkService.remove(request.getChatId(), request.getLink());
            responseObserver.onNext(toGrpcLinkResponse(response));
            responseObserver.onCompleted();
        } catch (ChatNotFoundException | LinkNotFoundException ex) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    /**
     * Возвращает список ссылок чата через gRPC API.
     *
     * @param request запрос списка ссылок
     * @param responseObserver observer ответа
     */
    @Override
    public void listLinks(ChatIdRequest request, StreamObserver<ListLinksResponse> responseObserver) {
        try {
            List<LinkResponse> links = linkService.list(request.getChatId());
            ListLinksResponse response = ListLinksResponse.newBuilder()
                    .addAllLinks(links.stream()
                            .map(ScrapperGrpcService::toGrpcLinkResponse)
                            .toList())
                    .setSize(links.size())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (ChatNotFoundException ex) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    /**
     * Конвертирует внутренний DTO ссылки в gRPC DTO.
     *
     * @param response внутренний DTO ссылки
     * @return gRPC DTO ссылки
     */
    private static backend.academy.linktracker.grpc.LinkResponse toGrpcLinkResponse(LinkResponse response) {
        return backend.academy.linktracker.grpc.LinkResponse.newBuilder()
                .setId(response.id())
                .setUrl(response.url())
                .addAllTags(response.tags() == null ? List.of() : response.tags())
                .addAllFilters(response.filters() == null ? List.of() : response.filters())
                .build();
    }
}
