package backend.academy.linktracker.scrapper.service;

/**
 * Нормализует и валидирует поля входящих запросов.
 */
final class RequestFieldNormalizer {

    /**
     * Максимальная допустимая длина URL ссылки.
     */
    private static final int MAX_LINK_LENGTH = 2048;
    /**
     * Максимальная допустимая длина пользовательского тега.
     */
    private static final int MAX_TAG_LENGTH = 128;

    private RequestFieldNormalizer() {}

    /**
     * Нормализует и валидирует URL ссылки.
     *
     * @param rawLink исходное строковое значение ссылки
     * @return нормализованная ссылка
     */
    static String normalizeLink(String rawLink) {
        return normalizeField(rawLink, "link", MAX_LINK_LENGTH);
    }

    /**
     * Нормализует и валидирует пользовательский тег.
     *
     * @param rawTag исходное строковое значение тега
     * @return нормализованный тег
     */
    static String normalizeTag(String rawTag) {
        return normalizeField(rawTag, "tag", MAX_TAG_LENGTH);
    }

    private static String normalizeField(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
