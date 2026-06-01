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
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-реализация клиента взаимодействия с scrapper.
 */
public class GrpcScrapperClient implements ScrapperClient {
    /**
     * Имя scope для метрик синхронных gRPC вызовов scrapper API.
     * Нужен как стабильный идентификатор измерений клиентской латентности.
     */
    private static final String SCOPE_SCRAPPER_SYNC_API = "scrapper_sync_api";
    /**
     * Таймаут по умолчанию для gRPC deadline.
     * Нужен как безопасный fallback при прямом создании клиента без внешнего конфига.
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    /**
     * gRPC-статусы бизнес-ошибки «ссылка уже отслеживается».
     * Нужны для маппинга add-операции в доменную ошибку.
     */
    private static final Set<Status.Code> ALREADY_TRACKED_GRPC_STATUSES = Set.of(Status.Code.ALREADY_EXISTS);
    /**
     * gRPC-статусы бизнес-ошибки «ссылка не найдена».
     * Нужны для маппинга remove-операции в доменную ошибку.
     */
    private static final Set<Status.Code> LINK_NOT_FOUND_GRPC_STATUSES = Set.of(Status.Code.NOT_FOUND);

    /**
     * Блокирующий gRPC stub сервиса ScrapperApi.
     */
    private final ScrapperApiGrpc.ScrapperApiBlockingStub stub;

    /**
     * Recorder клиентских метрик bot.
     */
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

    /**
     * Создает gRPC-клиент scrapper с заданным deadline.
     *
     * @param managedChannel gRPC-канал до scrapper-сервиса
     * @param timeout timeout/deadline вызовов
     * @param metricsRecorder recorder клиентских метрик
     * @implNote Нужен для параметризации deadline из конфигурации без дублирования в конфигурационных классах.
     */
    public GrpcScrapperClient(ManagedChannel managedChannel, Duration timeout, BotMetricsRecorder metricsRecorder) {
        this.stub = ScrapperApiGrpc.newBlockingStub(managedChannel)
                .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS);
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Регистрирует Telegram-чат в scrapper по gRPC.
     *
     * @param chatId идентификатор чата
     * @implNote Нужен для инициализации чат-контекста перед пользовательскими командами бота.
     */
    @Override
    public void registerChat(long chatId) {
        metricsRecorder.recordDuration(
                SCOPE_SCRAPPER_SYNC_API,
                "registerChat",
                () -> invokeRegisterChatCall(() -> stub.registerChat(
                        ChatIdRequest.newBuilder().setChatId(chatId).build())));
    }

    /**
     * Добавляет ссылку в трекинг через gRPC-вызов scrapper.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для отслеживания
     * @param tags теги подписки
     * @param filters фильтры подписки
     * @return созданная подписка
     * @implNote Нужен как синхронный шаг сценария `/track`, чтобы пользователь сразу получал результат.
     */
    @Override
    public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
        String normalizedLink = ScrapperClientInputSanitizer.normalizeLink(link);
        List<String> normalizedTags = ScrapperClientInputSanitizer.normalizeTags(tags);
        List<String> normalizedFilters = ScrapperClientInputSanitizer.normalizeFilters(filters);
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "addLink", () -> {
            try {
                backend.academy.linktracker.grpc.LinkResponse response = stub.addLink(AddLinkRequest.newBuilder()
                        .setChatId(chatId)
                        .setLink(normalizedLink)
                        .addAllTags(normalizedTags)
                        .addAllFilters(normalizedFilters)
                        .build());
                return toLinkResponse(response);
            } catch (StatusRuntimeException ex) {
                if (ALREADY_TRACKED_GRPC_STATUSES.contains(ex.getStatus().getCode())) {
                    throw new LinkAlreadyTrackedException();
                }
                throw ScrapperClientErrorMapper.mapGrpcFailure("Failed to add link in scrapper over gRPC", ex);
            }
        });
    }

    /**
     * Удаляет ссылку из трекинга через gRPC-вызов scrapper.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для удаления
     * @return удаленная подписка
     * @implNote Нужен для команды `/untrack` с корректным разделением бизнес- и транспортных ошибок.
     */
    @Override
    public LinkResponse removeLink(long chatId, String link) {
        String normalizedLink = ScrapperClientInputSanitizer.normalizeLink(link);
        return metricsRecorder.recordDuration(SCOPE_SCRAPPER_SYNC_API, "removeLink", () -> {
            try {
                backend.academy.linktracker.grpc.LinkResponse response = stub.removeLink(RemoveLinkRequest.newBuilder()
                        .setChatId(chatId)
                        .setLink(normalizedLink)
                        .build());
                return toLinkResponse(response);
            } catch (StatusRuntimeException ex) {
                if (LINK_NOT_FOUND_GRPC_STATUSES.contains(ex.getStatus().getCode())) {
                    throw new LinkNotFoundException();
                }
                throw ScrapperClientErrorMapper.mapGrpcFailure("Failed to remove link in scrapper over gRPC", ex);
            }
        });
    }

    /**
     * Получает список отслеживаемых ссылок чата через gRPC.
     *
     * @param chatId идентификатор чата
     * @return список ссылок чата
     * @implNote Нужен для команды `/list` при gRPC-транспорте scrapper-клиента.
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
                throw ScrapperClientErrorMapper.mapGrpcFailure("Failed to list links in scrapper over gRPC", ex);
            }
        });
    }

    /**
     * Выполняет gRPC-вызов регистрации чата с унифицированной обработкой ошибок.
     *
     * @param call операция gRPC-вызова
     * @implNote Нужен, чтобы не дублировать одинаковую обработку ALREADY_EXISTS/transport error в нескольких местах.
     */
    private void invokeRegisterChatCall(RegisterChatCall call) {
        try {
            call.run();
        } catch (StatusRuntimeException ex) {
            if (ALREADY_TRACKED_GRPC_STATUSES.contains(ex.getStatus().getCode())) {
                return;
            }
            throw ScrapperClientErrorMapper.mapGrpcFailure("Failed to register chat in scrapper over gRPC", ex);
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
    private interface RegisterChatCall {
        /**
         * Выполняет gRPC-вызов.
         *
         * @return пустой ответ gRPC
         * @implNote Нужен для передачи операции регистрации в обертку обработки ошибок.
         */
        EmptyMessage run();
    }
}
