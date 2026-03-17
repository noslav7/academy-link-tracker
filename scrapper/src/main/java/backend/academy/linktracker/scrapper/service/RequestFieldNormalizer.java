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
        if (rawLink == null || rawLink.isBlank()) {
            throw new IllegalArgumentException("link is required");
        }
        String normalized = rawLink.trim();
        if (normalized.length() > MAX_LINK_LENGTH) {
            throw new IllegalArgumentException("link length exceeds " + MAX_LINK_LENGTH + " characters");
        }
        return normalized;
    }

    /**
     * Нормализует и валидирует пользовательский тег.
     *
     * @param rawTag исходное строковое значение тега
     * @return нормализованный тег
     */
    static String normalizeTag(String rawTag) {
        if (rawTag == null || rawTag.isBlank()) {
            throw new IllegalArgumentException("tag is required");
        }
        String normalizedTag = rawTag.trim();
        if (normalizedTag.length() > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException("tag length exceeds " + MAX_TAG_LENGTH + " characters");
        }
        return normalizedTag;
    }
}
