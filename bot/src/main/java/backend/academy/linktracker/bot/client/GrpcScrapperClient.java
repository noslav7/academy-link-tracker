package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatIdRequest;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.ScrapperApiGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;

public class GrpcScrapperClient implements ScrapperClient {

    private final ScrapperApiGrpc.ScrapperApiBlockingStub stub;

    public GrpcScrapperClient(ManagedChannel managedChannel) {
        this.stub = ScrapperApiGrpc.newBlockingStub(managedChannel);
    }

    @Override
    public void registerChat(long chatId) {
        invoke(() ->
                stub.registerChat(ChatIdRequest.newBuilder().setChatId(chatId).build()));
    }

    @Override
    public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
        try {
            backend.academy.linktracker.grpc.LinkResponse response = stub.addLink(AddLinkRequest.newBuilder()
                    .setChatId(chatId)
                    .setLink(link)
                    .addAllTags(tags)
                    .addAllFilters(filters)
                    .build());
            return toLinkResponse(response);
        } catch (StatusRuntimeException ex) {
            if (Status.ALREADY_EXISTS.getCode().equals(ex.getStatus().getCode())) {
                throw new LinkAlreadyTrackedException();
            }
            throw new ScrapperClientException("Failed to add link in scrapper over gRPC", ex);
        }
    }

    @Override
    public LinkResponse removeLink(long chatId, String link) {
        try {
            backend.academy.linktracker.grpc.LinkResponse response = stub.removeLink(RemoveLinkRequest.newBuilder()
                    .setChatId(chatId)
                    .setLink(link)
                    .build());
            return toLinkResponse(response);
        } catch (StatusRuntimeException ex) {
            if (Status.NOT_FOUND.getCode().equals(ex.getStatus().getCode())) {
                throw new LinkNotFoundException();
            }
            throw new ScrapperClientException("Failed to remove link in scrapper over gRPC", ex);
        }
    }

    @Override
    public List<LinkResponse> getLinks(long chatId) {
        try {
            ListLinksResponse response =
                    stub.listLinks(ChatIdRequest.newBuilder().setChatId(chatId).build());
            return response.getLinksList().stream()
                    .map(GrpcScrapperClient::toLinkResponse)
                    .toList();
        } catch (StatusRuntimeException ex) {
            throw new ScrapperClientException("Failed to list links in scrapper over gRPC", ex);
        }
    }

    private void invoke(RunnableCall call) {
        try {
            call.run();
        } catch (StatusRuntimeException ex) {
            if (Status.ALREADY_EXISTS.getCode().equals(ex.getStatus().getCode())) {
                return;
            }
            throw new ScrapperClientException("Failed to register chat in scrapper over gRPC", ex);
        }
    }

    private static LinkResponse toLinkResponse(backend.academy.linktracker.grpc.LinkResponse response) {
        return new LinkResponse(
                response.getId(),
                response.getUrl(),
                List.copyOf(response.getTagsList()),
                List.copyOf(response.getFiltersList()));
    }

    @FunctionalInterface
    private interface RunnableCall {
        EmptyMessage run();
    }
}
