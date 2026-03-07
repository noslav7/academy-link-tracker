package backend.academy.linktracker.bot.service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Проверяет и разбирает пользовательский ввод для диалога {@code /track}.
 */
@Component
public class TrackDialogInputParser {

    /**
     * Максимально допустимая длина ссылки в диалоге.
     */
    private static final int MAX_TRACK_LINK_LENGTH = 2048;
    /**
     * Допустимые URI-схемы для отслеживаемых ссылок.
     */
    private static final Set<String> SUPPORTED_URL_SCHEMES = Set.of("http", "https");

    /**
     * Преобразует CSV-строку в список значений.
     *
     * @param input исходная строка (например, {@code "tag1, tag2"})
     * @return список непустых значений; для {@code null}, пустой строки или {@code "-"} возвращается пустой список
     */
    public List<String> parseCsvValues(String input) {
        if (input == null) {
            return List.of();
        }
        String normalized = input.trim();
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return List.of();
        }
        return List.of(normalized.split(",")).stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    /**
     * Проверяет, что строка является корректным URL для отслеживания.
     *
     * @param value строковое значение ссылки
     * @return {@code true}, если ссылка валидна и поддерживается
     */
    public boolean isValidUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > MAX_TRACK_LINK_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(normalizedValue);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return false;
            }
            return SUPPORTED_URL_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
