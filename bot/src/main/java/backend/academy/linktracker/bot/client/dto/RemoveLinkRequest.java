package backend.academy.linktracker.bot.client.dto;

/**
 * DTO запроса удаления ссылки из отслеживания.
 *
 * @param link URL ссылки для удаления
 */
public record RemoveLinkRequest(String link) {}
