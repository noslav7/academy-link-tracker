package backend.academy.linktracker.scrapper.dto;

import java.util.List;

/**
 * DTO ответа со списком отслеживаемых ссылок.
 *
 * @param links список ссылок
 * @param size размер списка
 */
public record ListLinksResponse(List<LinkResponse> links, int size) {}
