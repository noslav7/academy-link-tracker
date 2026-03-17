package backend.academy.linktracker.scrapper.dto;

/**
 * DTO запроса на обновление тега у существующей подписки.
 *
 * @param link URL отслеживаемой ссылки
 * @param oldTag текущее значение тега
 * @param newTag новое значение тега
 */
public record UpdateTagRequest(String link, String oldTag, String newTag) {}
