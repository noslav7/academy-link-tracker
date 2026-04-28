package backend.academy.linktracker.scrapper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO запроса на обновление тега у существующей подписки.
 *
 * @param link URL отслеживаемой ссылки
 * @param oldTag текущее значение тега
 * @param newTag новое значение тега
 */
public record UpdateTagRequest(
        @NotBlank @Size(max = 2048) String link,
        @NotBlank @Size(max = 128) String oldTag,
        @NotBlank @Size(max = 128) String newTag) {}
