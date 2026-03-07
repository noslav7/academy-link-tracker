package backend.academy.linktracker.scrapper.dto;

import java.util.List;

/**
 * DTO ответа со сведениями об отслеживаемой ссылке.
 *
 * @param id идентификатор ссылки
 * @param url URL ссылки
 * @param tags теги подписки
 * @param filters фильтры подписки
 */
public record LinkResponse(long id, String url, List<String> tags, List<String> filters) {}
