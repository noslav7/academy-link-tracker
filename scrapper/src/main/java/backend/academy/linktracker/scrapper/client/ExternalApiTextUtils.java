package backend.academy.linktracker.scrapper.client;

/**
 * Общие утилиты нормализации строк на границе внешних API.
 */
final class ExternalApiTextUtils {

    private ExternalApiTextUtils() {}

    /**
     * Проверяет, что строка содержит непустой текст.
     *
     * @param value исходное значение
     * @return {@code true}, если в строке есть полезные символы
     * @implNote Нужен для единообразной валидации ответов внешних API в нескольких клиентах.
     */
    static boolean hasTextValue(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Возвращает исходное значение, если в нем есть текст, иначе fallback.
     *
     * @param value исходное значение
     * @param fallback запасное значение
     * @return нормализованное строковое значение
     * @implNote Нужен, чтобы не дублировать null/blank-обработку в GitHub и StackOverflow клиентах.
     */
    static String normalizeOrFallback(String value, String fallback) {
        if (!hasTextValue(value)) {
            return fallback;
        }
        return value;
    }
}
