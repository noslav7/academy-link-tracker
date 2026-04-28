package backend.academy.linktracker.scrapper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO запроса на удаление тега у существующей подписки.
 *
 * @param link URL отслеживаемой ссылки
 * @param tag значение удаляемого тега
 */
public record RemoveTagRequest(@NotBlank @Size(max = 2048) String link, @NotBlank @Size(max = 128) String tag) {}
