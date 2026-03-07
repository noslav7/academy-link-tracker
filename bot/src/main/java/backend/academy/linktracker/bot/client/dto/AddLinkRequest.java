package backend.academy.linktracker.bot.client.dto;

import java.util.List;

/**
 * DTO запроса добавления ссылки в scrapper.
 *
 * @param link URL ссылки
 * @param tags список тегов
 * @param filters список фильтров
 */
public record AddLinkRequest(String link, List<String> tags, List<String> filters) {}
