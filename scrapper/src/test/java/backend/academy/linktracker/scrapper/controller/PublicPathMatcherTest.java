package backend.academy.linktracker.scrapper.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.properties.RateLimitingProperties;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class PublicPathMatcherTest {

    @ParameterizedTest
    @ValueSource(strings = {"/links", "/tg-chat/7001"})
    void givenConfiguredPublicPathWhenMatchingThenReturnsTrue(String requestPath) {
        // Проверяет, что matcher принимает допустимые публичные endpoint (exact и wildcard).
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setPublicPaths(List.of("/links", "/tg-chat/**", "/tags/{id}"));
        PublicPathMatcher matcher = new PublicPathMatcher(properties);

        assertTrue(matcher.matches(requestPath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/health", "/actuator/prometheus"})
    void givenNonPublicPathWhenMatchingThenReturnsFalse(String requestPath) {
        // Проверяет, что непубличные маршруты не попадают под rate limiting.
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setPublicPaths(List.of("/links", "/tg-chat/**", "/tags/{id}"));
        PublicPathMatcher matcher = new PublicPathMatcher(properties);

        assertFalse(matcher.matches(requestPath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void givenBlankPathWhenMatchingThenReturnsFalse(String requestPath) {
        // Проверяет граничный случай: пустой путь должен быть отвергнут.
        RateLimitingProperties properties = new RateLimitingProperties();
        PublicPathMatcher matcher = new PublicPathMatcher(properties);

        assertFalse(matcher.matches(requestPath));
    }

    @Test
    void givenNullPathWhenMatchingThenReturnsFalse() {
        // Проверяет граничный случай: null-путь не должен считаться публичным.
        RateLimitingProperties properties = new RateLimitingProperties();
        PublicPathMatcher matcher = new PublicPathMatcher(properties);

        assertFalse(matcher.matches(null));
    }
}
