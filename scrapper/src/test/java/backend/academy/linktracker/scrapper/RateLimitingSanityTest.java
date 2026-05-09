package backend.academy.linktracker.scrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.controller.PublicPathMatcher;
import backend.academy.linktracker.scrapper.properties.RateLimitingProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sanity")
@Tag("unit")
class RateLimitingSanityTest {

    @Test
    @DisplayName("givenConfiguredPublicAndPrivatePathsWhenMatchingThenReturnsExpectedFlags")
    void givenConfiguredPublicAndPrivatePathsWhenMatchingThenReturnsExpectedFlags() {
        // Быстрая sanity-проверка: matcher отличает публичный endpoint от внутреннего.
        RateLimitingProperties properties = new RateLimitingProperties();
        properties.setPublicPaths(List.of("/tg-chat/**", "/links"));
        PublicPathMatcher matcher = new PublicPathMatcher(properties);

        assertAllPaths(matcher);
    }

    @Test
    @DisplayName("givenDefaultRateLimitPropertiesWhenCreatedThenContainsExpectedDefaults")
    void givenDefaultRateLimitPropertiesWhenCreatedThenContainsExpectedDefaults() {
        // Быстрая sanity-проверка: значения по умолчанию не сломаны.
        RateLimitingProperties properties = new RateLimitingProperties();

        assertEquals(100, properties.getLimitForPeriod());
        assertTrue(properties.isEnabled());
    }

    private void assertAllPaths(PublicPathMatcher matcher) {
        assertTrue(matcher.matches("/tg-chat/9001"));
        assertTrue(matcher.matches("/links"));
        assertFalse(matcher.matches("/internal/health"));
    }
}
