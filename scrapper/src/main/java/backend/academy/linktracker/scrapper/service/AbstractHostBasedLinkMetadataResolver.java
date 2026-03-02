package backend.academy.linktracker.scrapper.service;

import java.net.URI;
import java.util.List;

/**
 * Базовый шаблон резолвера, который работает с конкретным хостом и минимальным числом сегментов пути.
 */
public abstract class AbstractHostBasedLinkMetadataResolver implements LinkFormatMetadataResolver {

    /**
     * Часть имени хоста, которую должен содержать URI.
     */
    private final String requiredHostPart;
    /**
     * Минимальное количество сегментов пути, необходимых для обработки URI.
     */
    private final int minimumPathSegmentsCount;

    /**
     * Создает базовый резолвер для указанного хоста и ограничения по сегментам пути.
     *
     * @param requiredHostPart часть имени хоста, которую должен содержать URI
     * @param minimumPathSegmentsCount минимальное количество сегментов пути
     */
    protected AbstractHostBasedLinkMetadataResolver(String requiredHostPart, int minimumPathSegmentsCount) {
        this.requiredHostPart = requiredHostPart;
        this.minimumPathSegmentsCount = minimumPathSegmentsCount;
    }

    /**
     * Проверяет, поддерживается ли переданный URI базовыми правилами хоста и пути.
     *
     * @param uri ссылка в формате URI
     * @return {@code true}, если URI поддерживается резолвером
     */
    @Override
    public final boolean supports(URI uri) {
        if (!hasRequiredHost(uri)) {
            return false;
        }
        List<String> segments = extractPathSegments(uri);
        if (segments.size() < minimumPathSegmentsCount) {
            return false;
        }
        return supportsPathSegments(segments);
    }

    /**
     * Возвращает сегменты пути URI для переиспользования в наследниках.
     *
     * @param uri исходный URI
     * @return непустые сегменты пути URI
     */
    protected final List<String> extractPathSegments(URI uri) {
        return LinkUriPathSegmentsExtractor.extract(uri);
    }

    /**
     * Дополнительная проверка структуры пути в наследнике.
     *
     * @param pathSegments сегменты пути URI
     * @return {@code true}, если путь удовлетворяет правилам конкретного формата
     */
    protected boolean supportsPathSegments(List<String> pathSegments) {
        return true;
    }

    /**
     * Проверяет соответствие хоста базовому шаблону.
     *
     * @param uri исходный URI
     * @return {@code true}, если хост подходит для резолвера
     */
    private boolean hasRequiredHost(URI uri) {
        String host = uri.getHost();
        return host != null && host.contains(requiredHostPart);
    }
}
