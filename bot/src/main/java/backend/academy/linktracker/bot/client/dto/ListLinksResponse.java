package backend.academy.linktracker.bot.client.dto;

import java.util.List;

/**
 * DTO ответа scrapper со списком ссылок чата.
 *
 * @param links список ссылок
 * @param size размер списка
 */
public record ListLinksResponse(List<LinkResponse> links, int size) {}
