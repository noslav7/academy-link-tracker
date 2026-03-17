package backend.academy.linktracker.scrapper.dto;

import java.util.List;

/**
 * DTO ответа со списком тегов подписки.
 *
 * @param tags список тегов
 * @param size количество тегов
 */
public record ListTagsResponse(List<String> tags, int size) {}
