package backend.academy.linktracker.scrapper.dto;

/**
 * DTO запроса на добавление тега к существующей подписке.
 *
 * @param link URL отслеживаемой ссылки
 * @param tag значение добавляемого тега
 */
public record AddTagRequest(String link, String tag) {}
