package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.exception.UnsupportedLinkException;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Фабрика выбора резолвера метаданных по формату ссылки.
 */
@Component
public class LinkFormatMetadataResolverFactory {

    /**
     * Доступные резолверы форматов ссылок.
     */
    private final List<LinkFormatMetadataResolver> resolvers;

    /**
     * Создает фабрику резолверов форматов ссылок.
     *
     * @param resolvers список резолверов в порядке приоритета
     */
    public LinkFormatMetadataResolverFactory(List<LinkFormatMetadataResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    /**
     * Возвращает резолвер, который поддерживает переданный URI.
     *
     * @param uri ссылка в формате URI
     * @return выбранный резолвер
     */
    public LinkFormatMetadataResolver getResolver(URI uri) {
        return resolvers.stream()
                .filter(resolver -> resolver.supports(uri))
                .findFirst()
                .orElseThrow(() -> new UnsupportedLinkException(uri.toString()));
    }
}
