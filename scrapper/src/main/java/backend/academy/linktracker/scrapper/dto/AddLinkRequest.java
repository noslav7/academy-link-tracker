package backend.academy.linktracker.scrapper.dto;

import java.util.List;

/**
 * DTO запроса на добавление ссылки в отслеживание.
 *
 * @param link URL ссылки для отслеживания
 * @param tags список пользовательских тегов
 * @param filters список фильтров
 */
public record AddLinkRequest(String link, List<String> tags, List<String> filters) {}
