package backend.academy.linktracker.bot.client;

import java.util.List;

/**
 * Нормализует и валидирует входные строковые данные перед вызовами scrapper.
 */
final class ScrapperClientInputSanitizer {
    /**
     * Максимальная допустимая длина URL в операциях трекинга.
     * Нужна, чтобы отбраковывать заведомо некорректный ввод до сетевого вызова.
     */
    private static final int MAX_LINK_LENGTH = 2048;
    /**
     * Максимальная допустимая длина одного тега.
     * Нужна для защиты от чрезмерно длинных пользовательских значений.
     */
    private static final int MAX_TAG_LENGTH = 128;
    /**
     * Максимальная допустимая длина одного фильтра.
     * Нужна для единых ограничений пользовательского ввода в клиентах bot.
     */
    private static final int MAX_FILTER_LENGTH = 256;

    private ScrapperClientInputSanitizer() {}

    /**
     * Валидирует и нормализует ссылку трекинга.
     *
     * @param link сырая ссылка из пользовательского ввода
     * @return очищенная и проверенная ссылка
     * @implNote Нужен fail-fast до вызова scrapper, чтобы не отправлять невалидные данные в транспортный слой.
     */
    static String normalizeLink(String link) {
        return normalizeRequiredField(link, "link", MAX_LINK_LENGTH);
    }

    /**
     * Валидирует и нормализует набор тегов.
     *
     * @param tags список тегов пользователя
     * @return неизменяемый список валидных тегов
     * @implNote Нужен для унификации правил валидации между REST и gRPC клиентами.
     */
    static List<String> normalizeTags(List<String> tags) {
        return normalizeOptionalCollection(tags, "tag", MAX_TAG_LENGTH);
    }

    /**
     * Валидирует и нормализует набор фильтров.
     *
     * @param filters список фильтров пользователя
     * @return неизменяемый список валидных фильтров
     * @implNote Нужен для устранения дублирования валидации в нескольких клиентах.
     */
    static List<String> normalizeFilters(List<String> filters) {
        return normalizeOptionalCollection(filters, "filter", MAX_FILTER_LENGTH);
    }

    /**
     * Приводит обязательное строковое поле к нормализованному виду и проверяет ограничения.
     *
     * @param value исходное значение поля
     * @param fieldName человекочитаемое имя поля
     * @param maxLength максимально допустимая длина
     * @return нормализованное значение поля
     */
    private static String normalizeRequiredField(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be <= " + maxLength);
        }
        return normalized;
    }

    /**
     * Нормализует опциональную коллекцию строковых значений.
     *
     * @param values исходные значения
     * @param fieldName имя поля для сообщений валидации
     * @param maxLength максимально допустимая длина элемента
     * @return пустой или валидированный список значений
     */
    private static List<String> normalizeOptionalCollection(List<String> values, String fieldName, int maxLength) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> normalizeRequiredField(value, fieldName, maxLength))
                .toList();
    }
}
