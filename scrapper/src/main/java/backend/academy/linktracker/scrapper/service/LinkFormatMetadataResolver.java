package backend.academy.linktracker.scrapper.service;

import java.net.URI;
import java.time.Instant;

/**
 * Контракт резолвера метаданных для конкретного формата ссылки.
 */
public interface LinkFormatMetadataResolver {
    /**
     * Проверяет, поддерживает ли резолвер переданный URI.
     *
     * @param uri ссылка в формате URI
     * @return {@code true}, если формат URI поддерживается
     */
    boolean supports(URI uri);

    /**
     * Получает время последнего обновления ресурса по URI.
     *
     * @param uri ссылка в формате URI
     * @return время последнего обновления
     */
    Instant resolveLastUpdated(URI uri);
}
