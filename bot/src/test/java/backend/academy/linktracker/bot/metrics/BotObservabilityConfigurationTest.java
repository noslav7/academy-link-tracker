package backend.academy.linktracker.bot.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.UrlResource;

@Tag("unit")
@Tag("sanity")
class BotObservabilityConfigurationTest {

    private static final String APPLICATION_YAML = "application.yaml";
    private static final String MAIN_CLASSES_SEGMENT = "/target/classes/";

    @Test
    // Проверяет лейбл application у pushgateway.
    void givenMainApplicationYamlWhenLoadedThenPushgatewayApplicationLabelIsBot() throws IOException {
        PropertySource<?> propertySource = loadApplicationProperties();
        assertEquals(
                "bot",
                propertySource.getProperty(
                        "management.prometheus.metrics.export.pushgateway.grouping-key.application"));
    }

    @Test
    // Проверяет лейбл app_type у pushgateway.
    void givenMainApplicationYamlWhenLoadedThenPushgatewayAppTypeLabelIsBot() throws IOException {
        PropertySource<?> propertySource = loadApplicationProperties();
        assertEquals(
                "bot",
                propertySource.getProperty("management.prometheus.metrics.export.pushgateway.grouping-key.app_type"));
    }

    @Test
    // Проверяет алиас пути до prometheus endpoint.
    void givenMainApplicationYamlWhenLoadedThenPrometheusPathMappingIsMetrics() throws IOException {
        PropertySource<?> propertySource = loadApplicationProperties();
        assertEquals("metrics", propertySource.getProperty("management.endpoints.web.path-mapping.prometheus"));
    }

    @Test
    // Проверяет env-плейсхолдер для включения pushgateway.
    void givenMainApplicationYamlWhenLoadedThenPushgatewayEnabledPlaceholderUsesBotEnv() throws IOException {
        PropertySource<?> propertySource = loadApplicationProperties();
        assertEquals(
                "${BOT_PUSHGATEWAY_ENABLED:true}",
                propertySource.getProperty("management.prometheus.metrics.export.pushgateway.enabled"));
    }

    @Test
    // Проверяет env-плейсхолдер для URL pushgateway.
    void givenMainApplicationYamlWhenLoadedThenPushgatewayBaseUrlPlaceholderUsesBotEnv() throws IOException {
        PropertySource<?> propertySource = loadApplicationProperties();
        assertEquals(
                "${BOT_PUSHGATEWAY_BASE_URL:http://localhost:9091}",
                propertySource.getProperty("management.prometheus.metrics.export.pushgateway.base-url"));
    }

    @Test
    // Проверяет env-плейсхолдер для частоты отправки метрик.
    void givenMainApplicationYamlWhenLoadedThenPushgatewayPushRatePlaceholderUsesBotEnv() throws IOException {
        PropertySource<?> propertySource = loadApplicationProperties();
        assertEquals(
                "${BOT_PUSHGATEWAY_PUSH_RATE:15s}",
                propertySource.getProperty("management.prometheus.metrics.export.pushgateway.push-rate"));
    }

    private PropertySource<?> loadApplicationProperties() throws IOException {
        UrlResource resource = locateMainApplicationYaml();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("bot-main-application", resource);
        return sources.getFirst();
    }

    private UrlResource locateMainApplicationYaml() throws IOException {
        Enumeration<URL> resources =
                Thread.currentThread().getContextClassLoader().getResources(APPLICATION_YAML);
        while (resources.hasMoreElements()) {
            URL candidate = resources.nextElement();
            String normalizedPath = candidate.toString().replace('\\', '/');
            if (normalizedPath.contains(MAIN_CLASSES_SEGMENT)) {
                return new UrlResource(candidate);
            }
        }
        throw new IllegalStateException("Cannot locate main application.yaml from target/classes");
    }
}
