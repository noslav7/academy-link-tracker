package backend.academy.linktracker.scrapper.dto;

/**
 * DTO запроса удаления ссылки.
 *
 * @param link URL ссылки для удаления
 */
public record RemoveLinkRequest(String link) {}
