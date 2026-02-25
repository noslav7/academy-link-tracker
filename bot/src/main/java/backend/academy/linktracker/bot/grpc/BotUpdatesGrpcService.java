package backend.academy.linktracker.bot.grpc;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.UpdateNotificationService;
import backend.academy.linktracker.grpc.BotUpdatesApiGrpc;
import backend.academy.linktracker.grpc.EmptyMessage;
import backend.academy.linktracker.grpc.LinkUpdateRequest;
import io.grpc.stub.StreamObserver;
import java.util.List;

public class BotUpdatesGrpcService extends BotUpdatesApiGrpc.BotUpdatesApiImplBase {

    private final UpdateNotificationService updateNotificationService;

    public BotUpdatesGrpcService(UpdateNotificationService updateNotificationService) {
        this.updateNotificationService = updateNotificationService;
    }

    @Override
    public void sendUpdate(LinkUpdateRequest request, StreamObserver<EmptyMessage> responseObserver) {
        updateNotificationService.notifyAboutUpdate(
                new LinkUpdate(request.getId(), request.getUrl(), request.getDescription(), List.copyOf(request.getTgChatIdsList())));
        responseObserver.onNext(EmptyMessage.newBuilder().build());
        responseObserver.onCompleted();
    }
}
