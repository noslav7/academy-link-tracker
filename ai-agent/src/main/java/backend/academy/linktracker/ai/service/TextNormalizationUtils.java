package backend.academy.linktracker.ai.service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Утилиты нормализации текста и конфигурационных списков для единообразных сравнений.
 */
public final class TextNormalizationUtils {

    private TextNormalizationUtils() {}

    /**
     * Нормализует строку для нечувствительного к регистру сравнения.
     *
     * @param value исходное значение
     * @return значение без краевых пробелов в нижнем регистре
     */
    public static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Нормализует набор конфигурационных значений в неизменяемое множество.
     *
     * @param values исходные значения
     * @return нормализованный набор без null/blank элементов
     */
    public static Set<String> normalizedConfiguredValues(Collection<String> values) {
        if (values == null) {
            return Collections.emptySet();
        }

        Set<String> normalizedValues = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalizedValues.add(normalize(value));
            }
        }
        return Collections.unmodifiableSet(normalizedValues);
    }

    /**
     * Проверяет, содержит ли текст хотя бы одно значение из набора.
     *
     * @param normalizedText нормализованный текст для поиска
     * @param patterns нормализованные шаблоны поиска
     * @return true, если найдено хотя бы одно совпадение
     */
    public static boolean containsAnySubstring(String normalizedText, Set<String> patterns) {
        for (String pattern : patterns) {
            if (normalizedText.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
