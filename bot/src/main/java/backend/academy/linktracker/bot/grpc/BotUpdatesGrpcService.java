package backend.academy.linktracker.bot.grpc;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.metrics.BotMetricsRecorder;
import backend.academy.linktracker.bot.service.UpdateNotificationService;
import backend.academy.linktracker.grpc.BotUpdatesApiGrpc;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import io.grpc.stub.StreamObserver;
import java.util.List;

/**
 * gRPC-сервис приема уведомлений об обновлениях от scrapper.
 */
public class BotUpdatesGrpcService extends BotUpdatesApiGrpc.BotUpdatesApiImplBase {
    private static final String SCOPE_SCRAPPER_ASYNC_API = "scrapper_async_api";
    private static final String SCOPE_TYPE_GRPC = "grpcSendUpdate";

    /**
     * Сервис рассылки уведомлений пользователям бота.
     */
    private final UpdateNotificationService updateNotificationService;

    private final BotMetricsRecorder metricsRecorder;

    /**
     * Создает gRPC-сервис уведомлений.
     *
     * @param updateNotificationService сервис рассылки уведомлений
     */
    public BotUpdatesGrpcService(UpdateNotificationService updateNotificationService) {
        this(updateNotificationService, BotMetricsRecorder.noop());
    }

    public BotUpdatesGrpcService(
            UpdateNotificationService updateNotificationService, BotMetricsRecorder metricsRecorder) {
        this.updateNotificationService = updateNotificationService;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Принимает gRPC-запрос об обновлении и отправляет уведомления в Telegram.
     *
     * @param request входной запрос с данными обновления
     * @param responseObserver observer ответа
     */
    @Override
    public void sendUpdate(LinkUpdateRequest request, StreamObserver<EmptyMessage> responseObserver) {
        metricsRecorder.recordDuration(
                SCOPE_SCRAPPER_ASYNC_API,
                SCOPE_TYPE_GRPC,
                () -> updateNotificationService.notifyAboutUpdate(new LinkUpdate(
                        request.getId(),
                        request.getUrl(),
                        request.getDescription(),
                        List.copyOf(request.getTgChatIdsList()))));
        responseObserver.onNext(EmptyMessage.newBuilder().build());
        responseObserver.onCompleted();
    }
}
