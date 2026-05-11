package backend.academy.linktracker.ai.service;

import org.springframework.stereotype.Service;

/**
 * Детерминированная заглушка AI API для первой итерации сервиса.
 */
@Service
public class StubUpdateSummarizer implements UpdateSummarizer {

    /**
     * Максимальная длина результата, нужна для предсказуемого размера заглушки ответа.
     */
    private static final int SUMMARY_MAX_LENGTH = 240;

    /**
     * Префикс результата, нужен для явного отличия сгенерированного summary от исходного текста.
     */
    private static final String SUMMARY_PREFIX = "Summary: ";

    /**
     * Суффикс усечения, нужен для обозначения неполного текста в детерминированной заглушке.
     */
    private static final String ELLIPSIS = "...";

    /**
     * Возвращает детерминированное краткое описание, чтобы core processing можно было тестировать без внешнего AI API.
     *
     * @param description исходный текст обновления
     * @return сокращенный текст
     */
    @Override
    public String summarize(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }

        String normalized = description.trim();
        int bodyMaxLength = SUMMARY_MAX_LENGTH - SUMMARY_PREFIX.length() - ELLIPSIS.length();
        if (normalized.length() <= bodyMaxLength) {
            return SUMMARY_PREFIX + normalized;
        }

        return SUMMARY_PREFIX + normalized.substring(0, bodyMaxLength).trim() + ELLIPSIS;
    }
}
