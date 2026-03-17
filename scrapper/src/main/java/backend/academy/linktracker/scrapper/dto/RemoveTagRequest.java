package backend.academy.linktracker.scrapper.dto;

/**
 * DTO запроса на удаление тега у существующей подписки.
 *
 * @param link URL отслеживаемой ссылки
 * @param tag значение удаляемого тега
 */
public record RemoveTagRequest(String link, String tag) {}
