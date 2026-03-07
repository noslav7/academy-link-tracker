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

/**
 * gRPC-реализация клиента взаимодействия с scrapper.
 */
public class GrpcScrapperClient implements ScrapperClient {

    /**
     * Блокирующий gRPC stub сервиса ScrapperApi.
     */
    private final ScrapperApiGrpc.ScrapperApiBlockingStub stub;

    /**
     * Создает gRPC-клиент scrapper.
     *
     * @param managedChannel gRPC-канал до scrapper-сервиса
     */
    public GrpcScrapperClient(ManagedChannel managedChannel) {
        this.stub = ScrapperApiGrpc.newBlockingStub(managedChannel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerChat(long chatId) {
        invoke(() ->
                stub.registerChat(ChatIdRequest.newBuilder().setChatId(chatId).build()));
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * Выполняет gRPC-вызов регистрации чата с унифицированной обработкой ошибок.
     *
     * @param call операция gRPC-вызова
     */
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

    /**
     * Преобразует gRPC DTO ссылки в клиентский DTO.
     *
     * @param response gRPC-ответ scrapper
     * @return клиентское представление ссылки
     */
    private static LinkResponse toLinkResponse(backend.academy.linktracker.grpc.LinkResponse response) {
        return new LinkResponse(
                response.getId(),
                response.getUrl(),
                List.copyOf(response.getTagsList()),
                List.copyOf(response.getFiltersList()));
    }

    /**
     * Контракт лямбды для вызова gRPC-операций без аргументов.
     */
    @FunctionalInterface
    private interface RunnableCall {
        /**
         * Выполняет gRPC-вызов.
         *
         * @return пустой ответ gRPC
         */
        EmptyMessage run();
    }
}
