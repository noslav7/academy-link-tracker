package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.bot.metrics.BotMetricsRecorder;
import backend.academy.linktracker.grpc.AddLinkRequest;
import backend.academy.linktracker.grpc.ChatIdRequest;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.ListLinksResponse;
import backend.academy.linktracker.grpc.RemoveLinkRequest;
import backend.academy.linktracker.grpc.ScrapperApiGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-реализация клиента взаимодействия с scrapper.
 */
public class GrpcScrapperClient implements ScrapperClient {
    private static final String SCOPE_SCRAPPER_SYNC_API = "scrapper_sync_api";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Блокирующий gRPC stub сервиса ScrapperApi.
     */
    private final ScrapperApiGrpc.ScrapperApiBlockingStub stub;

    private final BotMetricsRecorder metricsRecorder;

    /**
     * Создает gRPC-клиент scrapper.
     *
     * @param managedChannel gRPC-канал до scrapper-сервиса
     */
    public GrpcScrapperClient(ManagedChannel managedChannel) {
        this(managedChannel, BotMetricsRecorder.noop());
    }

    public GrpcScrapperClient(ManagedChannel managedChannel, BotMetricsRecorder metricsRecorder) {
        this(managedChannel, DEFAULT_TIMEOUT, metricsRecorder);
    }

    public GrpcScrapperClient(ManagedChannel managedChannel, Duration timeout, BotMetricsRecorder metricsRecorder) {
        this.stub = ScrapperApiGrpc.newBlockingStub(managedChannel)
                .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS);
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerChat(long chatId) {
        metricsRecorder.recordDuration(
                SCOPE_SCRAPPER_SYNC_API,
                "registerChat",
                () -> invoke(() -> stub.registerChat(
                        ChatIdRequest.newBuilder().setChatId(chatId).build())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "addLink", () -> {
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
                throw new ScrapperServiceUnavailableException("Failed to add link in scrapper over gRPC", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkResponse removeLink(long chatId, String link) {
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "removeLink", () -> {
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
                throw new ScrapperServiceUnavailableException("Failed to remove link in scrapper over gRPC", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LinkResponse> getLinks(long chatId) {
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "getLinks", () -> {
            try {
                ListLinksResponse response = stub.listLinks(
                        ChatIdRequest.newBuilder().setChatId(chatId).build());
                return response.getLinksList().stream()
                        .map(GrpcScrapperClient::toLinkResponse)
                        .toList();
            } catch (StatusRuntimeException ex) {
                throw new ScrapperServiceUnavailableException("Failed to list links in scrapper over gRPC", ex);
            }
        });
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
            throw new ScrapperServiceUnavailableException("Failed to register chat in scrapper over gRPC", ex);
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
