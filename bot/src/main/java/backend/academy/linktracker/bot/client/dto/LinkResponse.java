package backend.academy.linktracker.bot.client.dto;

import java.util.List;

/**
 * DTO ответа scrapper со сведениями об отслеживаемой ссылке.
 *
 * @param id идентификатор ссылки
 * @param url URL ссылки
 * @param tags теги ссылки
 * @param filters фильтры ссылки
 */
public record LinkResponse(long id, String url, List<String> tags, List<String> filters) {}
