package backend.academy.linktracker.scrapper.service;

import java.net.URI;
import java.util.List;

/**
 * Утилита для извлечения непустых сегментов пути из URI.
 */
final class LinkUriPathSegmentsExtractor {

    /**
     * Запрещает создание экземпляров утилитного класса.
     */
    private LinkUriPathSegmentsExtractor() {}

    /**
     * Возвращает непустые сегменты пути URI.
     *
     * @param uri исходный URI
     * @return список сегментов пути
     */
    static List<String> extract(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return List.of(path.split("/")).stream()
                .map(String::trim)
                .filter(segment -> !segment.isBlank())
                .toList();
    }
}
