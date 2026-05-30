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
import backend.academy.linktracker.scrapper.metrics.ScrapperMetricsRecorder;
import backend.academy.linktracker.scrapper.service.ChatService;
import backend.academy.linktracker.scrapper.service.LinkService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * gRPC-реализация API scrapper-сервиса.
 */
public class ScrapperGrpcService extends ScrapperApiGrpc.ScrapperApiImplBase {
    /**
     * Имя источника API-запросов для метрик scrapper.
     */
    private static final String GRPC_SOURCE = "grpc";
    /**
     * Исключения сценария регистрации чата, которые соответствуют gRPC-статусу ALREADY_EXISTS.
     */
    private static final Set<Class<? extends RuntimeException>> REGISTER_CHAT_ALREADY_EXISTS_EXCEPTIONS =
            Set.of(ChatAlreadyRegisteredException.class);
    /**
     * Исключения сценария добавления ссылки, которые соответствуют gRPC-статусу ALREADY_EXISTS.
     */
    private static final Set<Class<? extends RuntimeException>> ADD_LINK_ALREADY_EXISTS_EXCEPTIONS =
            Set.of(LinkAlreadyTrackedException.class);
    /**
     * Исключения сценария добавления ссылки, которые соответствуют gRPC-статусу NOT_FOUND.
     */
    private static final Set<Class<? extends RuntimeException>> ADD_LINK_NOT_FOUND_EXCEPTIONS =
            Set.of(ChatNotFoundException.class, LinkNotFoundException.class);
    /**
     * Исключения сценария добавления ссылки, которые соответствуют gRPC-статусу INVALID_ARGUMENT.
     */
    private static final Set<Class<? extends RuntimeException>> ADD_LINK_INVALID_ARGUMENT_EXCEPTIONS =
            Set.of(UnsupportedLinkException.class, IllegalArgumentException.class);
    /**
     * Исключения сценария удаления ссылки, которые соответствуют gRPC-статусу NOT_FOUND.
     */
    private static final Set<Class<? extends RuntimeException>> REMOVE_LINK_NOT_FOUND_EXCEPTIONS =
            Set.of(ChatNotFoundException.class, LinkNotFoundException.class);
    /**
     * Исключения сценария удаления ссылки, которые соответствуют gRPC-статусу INVALID_ARGUMENT.
     */
    private static final Set<Class<? extends RuntimeException>> REMOVE_LINK_INVALID_ARGUMENT_EXCEPTIONS =
            Set.of(IllegalArgumentException.class);
    /**
     * Исключения сценария получения списка ссылок, которые соответствуют gRPC-статусу NOT_FOUND.
     */
    private static final Set<Class<? extends RuntimeException>> LIST_LINKS_NOT_FOUND_EXCEPTIONS =
            Set.of(ChatNotFoundException.class);

    /**
     * Сервис регистрации/удаления чатов.
     */
    private final ChatService chatService;
    /**
     * Сервис управления отслеживаемыми ссылками.
     */
    private final LinkService linkService;
    /**
     * Recorder пользовательских метрик scrapper-сервиса.
     */
    private final ScrapperMetricsRecorder metricsRecorder;

    /**
     * Создает gRPC-сервис scrapper.
     *
     * @param chatService сервис чатов
     * @param linkService сервис ссылок
     */
    public ScrapperGrpcService(ChatService chatService, LinkService linkService) {
        this(chatService, linkService, ScrapperMetricsRecorder.noop());
    }

    public ScrapperGrpcService(
            ChatService chatService, LinkService linkService, ScrapperMetricsRecorder metricsRecorder) {
        this.chatService = chatService;
        this.linkService = linkService;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Регистрирует чат через gRPC API.
     *
     * @param request запрос регистрации чата
     * @param responseObserver observer ответа
     */
    @Override
    public void registerChat(ChatIdRequest request, StreamObserver<EmptyMessage> responseObserver) {
        metricsRecorder.incrementApiRequest(GRPC_SOURCE);
        executeWithErrorHandling(
                responseObserver,
                () -> {
                    chatService.register(request.getChatId());
                    return EmptyMessage.newBuilder().build();
                },
                this::statusForRegisterChat);
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
        metricsRecorder.incrementApiRequest(GRPC_SOURCE);
        executeWithErrorHandling(
                responseObserver,
                () -> toGrpcLinkResponse(linkService.add(
                        request.getChatId(),
                        new AddLinkRequest(
                                request.getLink(),
                                List.copyOf(request.getTagsList()),
                                List.copyOf(request.getFiltersList())))),
                this::statusForAddLink);
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
        metricsRecorder.incrementApiRequest(GRPC_SOURCE);
        executeWithErrorHandling(
                responseObserver,
                () -> toGrpcLinkResponse(linkService.remove(request.getChatId(), request.getLink())),
                this::statusForRemoveLink);
    }

    /**
     * Возвращает список ссылок чата через gRPC API.
     *
     * @param request запрос списка ссылок
     * @param responseObserver observer ответа
     */
    @Override
    public void listLinks(ChatIdRequest request, StreamObserver<ListLinksResponse> responseObserver) {
        metricsRecorder.incrementApiRequest(GRPC_SOURCE);
        executeWithErrorHandling(
                responseObserver,
                () -> {
                    List<LinkResponse> links = linkService.list(request.getChatId());
                    return ListLinksResponse.newBuilder()
                            .addAllLinks(links.stream()
                                    .map(ScrapperGrpcService::toGrpcLinkResponse)
                                    .toList())
                            .setSize(links.size())
                            .build();
                },
                this::statusForListLinks);
    }

    /**
     * Определяет gRPC-статус для ошибок регистрации чата.
     * Нужен, чтобы централизовать маппинг доменных исключений в транспортные статусы и не дублировать условия
     *     в endpoint-методе.
     *
     * @param exception исключение бизнес-операции
     * @return gRPC-статус ответа
     */
    private Status statusForRegisterChat(RuntimeException exception) {
        if (matchesAny(exception, REGISTER_CHAT_ALREADY_EXISTS_EXCEPTIONS)) {
            return Status.ALREADY_EXISTS;
        }
        return Status.INTERNAL;
    }

    /**
     * Определяет gRPC-статус для ошибок добавления ссылки.
     * Нужен, чтобы держать правила трансляции исключений в одном месте и упростить сопровождение endpoint-логики.
     *
     * @param exception исключение бизнес-операции
     * @return gRPC-статус ответа
     */
    private Status statusForAddLink(RuntimeException exception) {
        if (matchesAny(exception, ADD_LINK_ALREADY_EXISTS_EXCEPTIONS)) {
            return Status.ALREADY_EXISTS;
        }
        if (matchesAny(exception, ADD_LINK_NOT_FOUND_EXCEPTIONS)) {
            return Status.NOT_FOUND;
        }
        if (matchesAny(exception, ADD_LINK_INVALID_ARGUMENT_EXCEPTIONS)) {
            return Status.INVALID_ARGUMENT;
        }
        return Status.INTERNAL;
    }

    /**
     * Определяет gRPC-статус для ошибок удаления ссылки.
     * Нужен для единообразной обработки исключений и предсказуемого клиентского контракта.
     *
     * @param exception исключение бизнес-операции
     * @return gRPC-статус ответа
     */
    private Status statusForRemoveLink(RuntimeException exception) {
        if (matchesAny(exception, REMOVE_LINK_NOT_FOUND_EXCEPTIONS)) {
            return Status.NOT_FOUND;
        }
        if (matchesAny(exception, REMOVE_LINK_INVALID_ARGUMENT_EXCEPTIONS)) {
            return Status.INVALID_ARGUMENT;
        }
        return Status.INTERNAL;
    }

    /**
     * Определяет gRPC-статус для ошибок чтения списка ссылок.
     * Нужен, чтобы маршрут listLinks использовал тот же подход трансляции ошибок, что и остальные операции.
     *
     * @param exception исключение бизнес-операции
     * @return gRPC-статус ответа
     */
    private Status statusForListLinks(RuntimeException exception) {
        if (matchesAny(exception, LIST_LINKS_NOT_FOUND_EXCEPTIONS)) {
            return Status.NOT_FOUND;
        }
        return Status.INTERNAL;
    }

    /**
     * Проверяет, относится ли исключение (с учетом иерархии) к одному из ожидаемых типов.
     * Нужен, чтобы поддерживать компактные наборы правил маппинга без дублирования {@code instanceof} в каждом
     *     endpoint-обработчике.
     *
     * @param exception исходное исключение
     * @param expectedExceptionTypes ожидаемые типы исключений
     * @return {@code true}, если тип исключения входит в набор
     */
    private static boolean matchesAny(
            RuntimeException exception, Set<Class<? extends RuntimeException>> expectedExceptionTypes) {
        Class<?> currentType = exception.getClass();
        while (currentType != null && RuntimeException.class.isAssignableFrom(currentType)) {
            if (expectedExceptionTypes.contains(currentType)) {
                return true;
            }
            currentType = currentType.getSuperclass();
        }
        return false;
    }

    /**
     * Выполняет gRPC-операцию и централизованно обрабатывает ошибки.
     * Нужен как общий шаблон для endpoint-методов, чтобы исключить копипасту отправки ответа и маппинга ошибок.
     *
     * @param responseObserver observer gRPC-ответа
     * @param call бизнес-операция, возвращающая payload ответа
     * @param statusResolver преобразователь исключения в gRPC-статус
     * @param <T> тип payload ответа
     */
    private <T> void executeWithErrorHandling(
            StreamObserver<T> responseObserver, GrpcCall<T> call, Function<RuntimeException, Status> statusResolver) {
        try {
            T response = call.execute();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException exception) {
            metricsRecorder.incrementApiRequestError(GRPC_SOURCE);
            responseObserver.onError(statusResolver
                    .apply(exception)
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        }
    }

    @FunctionalInterface
    private interface GrpcCall<T> {
        /**
         * Выполняет бизнес-операцию gRPC endpoint-а.
         * Нужен для передачи лямбды в общий шаблон {@code executeWithErrorHandling}.
         *
         * @return payload ответа endpoint-а
         */
        T execute();
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
