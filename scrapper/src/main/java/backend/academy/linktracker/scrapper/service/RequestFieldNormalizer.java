package backend.academy.linktracker.scrapper.service;

import java.util.List;

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
    /**
     * Максимальная допустимая длина фильтра подписки.
     */
    private static final int MAX_FILTER_LENGTH = 256;

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

    /**
     * Нормализует и валидирует список тегов.
     *
     * @param rawTags исходный список тегов
     * @return неизменяемый список нормализованных тегов
     */
    static List<String> normalizeTags(List<String> rawTags) {
        return normalizeCollection(rawTags, "tag", MAX_TAG_LENGTH);
    }

    /**
     * Нормализует и валидирует список фильтров.
     *
     * @param rawFilters исходный список фильтров
     * @return неизменяемый список нормализованных фильтров
     */
    static List<String> normalizeFilters(List<String> rawFilters) {
        return normalizeCollection(rawFilters, "filter", MAX_FILTER_LENGTH);
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

    private static List<String> normalizeCollection(List<String> values, String fieldName, int maxLength) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> normalizeField(value, fieldName, maxLength))
                .toList();
    }
}
