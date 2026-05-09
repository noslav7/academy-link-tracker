package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.properties.RateLimitingProperties;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Определяет, подпадает ли URI под публичные endpoint rate limiting.
 *
 * <p>Нужен, чтобы централизовать правила сопоставления путей
 * и не дублировать их в HTTP-перехватчиках.
 */
@Component
public class PublicPathMatcher {

    /**
     * Сопоставитель ant-style шаблонов.
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * Набор точных URI без wildcard, проверяемых через contains.
     */
    private final Set<String> exactPublicPaths;

    /**
     * Список URI-шаблонов с wildcard, проверяемых через AntPathMatcher.
     */
    private final List<String> patternPublicPaths;

    /**
     * Создает matcher по конфигурации публичных путей.
     *
     * <p>Нужен, чтобы один раз разделить точные и шаблонные пути
     * и ускорить проверку на каждом запросе.
     *
     * @param properties свойства rate limiting
     */
    public PublicPathMatcher(RateLimitingProperties properties) {
        List<String> configuredPaths = List.copyOf(properties.getPublicPaths());
        this.exactPublicPaths = configuredPaths.stream()
                .filter(path -> !containsWildcard(path))
                .collect(Collectors.toUnmodifiableSet());
        this.patternPublicPaths = configuredPaths.stream()
                .filter(PublicPathMatcher::containsWildcard)
                .toList();
    }

    /**
     * Проверяет, является ли путь публичным для ограничения частоты.
     *
     * <p>Нужен, чтобы interceptor применял лимит только к целевым endpoint
     * и не затрагивал внутренние или служебные маршруты.
     *
     * @param requestPath путь запроса
     * @return {@code true}, если путь должен проходить через rate limiting
     */
    public boolean matches(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return false;
        }
        if (exactPublicPaths.contains(requestPath)) {
            return true;
        }
        return patternPublicPaths.stream().anyMatch(pathPattern -> PATH_MATCHER.match(pathPattern, requestPath));
    }

    /**
     * Определяет, содержит ли путь wildcard-символы.
     *
     * <p>Нужен, чтобы разделять точные и шаблонные правила и применять
     * наиболее простой алгоритм проверки там, где это возможно.
     *
     * @param path строка пути из конфигурации
     * @return {@code true}, если путь является шаблоном
     */
    private static boolean containsWildcard(String path) {
        return path.indexOf('*') >= 0 || path.indexOf('?') >= 0 || path.indexOf('{') >= 0;
    }
}
