package backend.academy.linktracker.scrapper.properties;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Конвертер значения transport из конфигурации в enum TransportMode.
 * Нужен, чтобы централизованно нормализовать формат значения и отбраковывать невалидный ввод до старта контекста.
 */
@Component
@ConfigurationPropertiesBinding
final class TransportModeConverter implements Converter<String, TransportMode> {

    /**
     * Преобразует строку транспорта в {@link TransportMode}.
     * Делает trim + upper-case, чтобы поддержать человеко-ориентированный ввод в конфигурации.
     *
     * @param source сырой текст из properties/yaml
     * @return нормализованный режим транспорта
     */
    @Override
    public TransportMode convert(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Transport mode must not be blank");
        }
        return TransportMode.fromSymbol(source.trim().toUpperCase(Locale.ROOT));
    }
}
