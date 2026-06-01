package backend.academy.linktracker.bot.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Централизованно маппит транспортные и доменные ошибки scrapper-клиента.
 */
final class ScrapperClientErrorMapper {
    /**
     * HTTP-статусы бизнес/клиентских ошибок scrapper (4xx).
     * Нужны, чтобы не смешивать доменную семантику и недоступность сервиса.
     */
    private static final Set<Integer> BUSINESS_HTTP_CLIENT_ERROR_STATUSES = businessHttpClientErrorStatuses();
    /**
     * Набор gRPC-кодов, считающихся транспортными сбоями.
     * Нужен для единообразного маппинга в {@link ScrapperServiceUnavailableException}.
     */
    private static final Set<Status.Code> TRANSPORT_ERROR_CODES = EnumSet.of(
            Status.Code.UNAVAILABLE,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.CANCELLED,
            Status.Code.RESOURCE_EXHAUSTED,
            Status.Code.UNKNOWN);

    private ScrapperClientErrorMapper() {}

    /**
     * Маппит REST-ошибку scrapper в доменное исключение.
     *
     * @param message контекст операции
     * @param exception исходное REST-исключение
     * @return доменное исключение нужной категории
     * @implNote Нужен, чтобы таймауты/сетевые ошибки не трактовались как бизнес-ошибки.
     */
    static RuntimeException mapRestFailure(String message, RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (BUSINESS_HTTP_CLIENT_ERROR_STATUSES.contains(status)) {
                return new ScrapperClientException(
                        message + ": scrapper returned business/client status " + status, responseException);
            }
        }
        return new ScrapperServiceUnavailableException(message, exception);
    }

    /**
     * Маппит gRPC-ошибку scrapper в доменное исключение.
     *
     * @param message контекст операции
     * @param exception исходное gRPC-исключение
     * @return доменное исключение нужной категории
     * @implNote Нужен для стабильно одинаковой семантики ошибок между REST и gRPC транспортами.
     */
    static RuntimeException mapGrpcFailure(String message, StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        if (TRANSPORT_ERROR_CODES.contains(code)) {
            return new ScrapperServiceUnavailableException(message, exception);
        }
        return new ScrapperClientException(message + ": scrapper returned gRPC status " + code, exception);
    }

    /**
     * Собирает неизменяемое множество клиентских HTTP-статусов.
     *
     * @return множество статусов 400..499
     * @implNote Нужен как единая точка формирования диапазона без «магических» условий в клиентских классах.
     */
    private static Set<Integer> businessHttpClientErrorStatuses() {
        Set<Integer> statuses = new HashSet<>();
        IntStream.rangeClosed(400, 499).forEach(statuses::add);
        return Collections.unmodifiableSet(statuses);
    }
}
