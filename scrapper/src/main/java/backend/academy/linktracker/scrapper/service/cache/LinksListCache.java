package backend.academy.linktracker.scrapper.service.cache;

import backend.academy.linktracker.scrapper.dto.ListLinksResponse;

/**
 * Абстракция кэша ответа GET /list по chatId.
 */
public interface LinksListCache {

    /**
     * Возвращает кэшированный ответ для chatId либо {@code null}, если записи нет.
     */
    ListLinksResponse get(long chatId);

    /**
     * Сохраняет ответ в кэше по chatId.
     */
    void put(long chatId, ListLinksResponse response);

    /**
     * Инвалидирует запись в кэше для chatId.
     */
    void evict(long chatId);

    /**
     * Полностью очищает кэш.
     */
    void clear();
}
