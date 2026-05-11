package backend.academy.linktracker.ai.service;

/**
 * Клиент суммаризации текста обновления.
 */
public interface UpdateSummarizer {

    /**
     * Суммаризирует текст обновления, чтобы длинные события можно было отправлять пользователю в компактном виде.
     *
     * @param description исходный текст
     * @return сокращенный текст
     */
    String summarize(String description);
}
