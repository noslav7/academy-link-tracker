package backend.academy.linktracker.scrapper.service;

import java.net.URI;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Выбирает подходящий форматный резолвер и делегирует ему получение метаданных ссылки.
 */
@Service
public class LinkMetadataResolver {

    /**
     * Фабрика выбора резолвера по URI.
     */
    private final LinkFormatMetadataResolverFactory resolverFactory;

    /**
     * Создает оркестратор резолверов метаданных ссылок.
     *
     * @param resolverFactory фабрика выбора резолвера по формату ссылки
     */
    public LinkMetadataResolver(LinkFormatMetadataResolverFactory resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    /**
     * Определяет формат ссылки и возвращает время последнего обновления.
     *
     * @param link строковое представление ссылки
     * @return время последнего обновления ресурса
     */
    public Instant resolveLastUpdated(String link) {
        URI uri = toNormalizedUri(link);
        return resolverFactory.getResolver(uri).resolveLastUpdated(uri);
    }

    /**
     * Возвращает человекочитаемое описание обновления ссылки.
     *
     * @param link строковое представление ссылки
     * @return описание обнаруженного обновления
     */
    public String resolveUpdateDescription(String link) {
        URI uri = toNormalizedUri(link);
        return resolverFactory.getResolver(uri).describeUpdate(uri);
    }

    private URI toNormalizedUri(String link) {
        return URI.create(RequestFieldNormalizer.normalizeLink(link));
    }
}
