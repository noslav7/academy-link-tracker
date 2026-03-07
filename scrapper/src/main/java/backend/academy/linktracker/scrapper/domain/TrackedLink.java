package backend.academy.linktracker.scrapper.domain;

import java.time.Instant;
import java.util.List;

/**
 * Доменная модель отслеживаемой ссылки, привязанной к чату.
 *
 * @param id внутренний идентификатор записи
 * @param chatId идентификатор Telegram-чата
 * @param url URL отслеживаемого ресурса
 * @param tags пользовательские теги
 * @param filters фильтры для подписки
 * @param lastCheckedAt время последней проверки ресурса
 * @param lastUpdatedAt время последнего известного обновления ресурса
 */
public record TrackedLink(
        long id,
        long chatId,
        String url,
        List<String> tags,
        List<String> filters,
        Instant lastCheckedAt,
        Instant lastUpdatedAt) {

    /**
     * Возвращает копию ссылки с обновленными временными метками проверки.
     *
     * @param checkedAt новое время проверки
     * @param updatedAt новое время последнего обновления
     * @return новый экземпляр с обновленными датами
     */
    public TrackedLink withLastCheck(Instant checkedAt, Instant updatedAt) {
        return new TrackedLink(id, chatId, url, tags, filters, checkedAt, updatedAt);
    }
}
