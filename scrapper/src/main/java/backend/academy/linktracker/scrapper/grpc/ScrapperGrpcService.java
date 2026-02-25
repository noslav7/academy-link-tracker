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

public class ScrapperGrpcService extends ScrapperApiGrpc.ScrapperApiImplBase {

    private final ChatService chatService;
    private final LinkService linkService;

    public ScrapperGrpcService(ChatService chatService, LinkService linkService) {
        this.chatService = chatService;
        this.linkService = linkService;
    }

    @Override
    public void registerChat(ChatIdRequest request, StreamObserver<EmptyMessage> responseObserver) {
        try {
            chatService.register(request.getChatId());
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        } catch (ChatAlreadyRegisteredException ex) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void addLink(
            backend.academy.linktracker.grpc.AddLinkRequest request,
            StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
        try {
            LinkResponse response = linkService.add(
                    request.getChatId(),
                    new AddLinkRequest(request.getLink(), List.copyOf(request.getTagsList()), List.copyOf(request.getFiltersList())));
            responseObserver.onNext(toGrpcLinkResponse(response));
            responseObserver.onCompleted();
        } catch (LinkAlreadyTrackedException ex) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
        } catch (ChatNotFoundException | LinkNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (UnsupportedLinkException | IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void removeLink(
            backend.academy.linktracker.grpc.RemoveLinkRequest request,
            StreamObserver<backend.academy.linktracker.grpc.LinkResponse> responseObserver) {
        try {
            LinkResponse response = linkService.remove(request.getChatId(), request.getLink());
            responseObserver.onNext(toGrpcLinkResponse(response));
            responseObserver.onCompleted();
        } catch (ChatNotFoundException | LinkNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listLinks(ChatIdRequest request, StreamObserver<ListLinksResponse> responseObserver) {
        try {
            List<LinkResponse> links = linkService.list(request.getChatId());
            ListLinksResponse response = ListLinksResponse.newBuilder()
                    .addAllLinks(links.stream().map(ScrapperGrpcService::toGrpcLinkResponse).toList())
                    .setSize(links.size())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (ChatNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    private static backend.academy.linktracker.grpc.LinkResponse toGrpcLinkResponse(LinkResponse response) {
        return backend.academy.linktracker.grpc.LinkResponse.newBuilder()
                .setId(response.id())
                .setUrl(response.url())
                .addAllTags(response.tags() == null ? List.of() : response.tags())
                .addAllFilters(response.filters() == null ? List.of() : response.filters())
                .build();
    }
}
