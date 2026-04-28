package backend.academy.linktracker.scrapper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO запроса удаления ссылки.
 *
 * @param link URL ссылки для удаления
 */
public record RemoveLinkRequest(@NotBlank @Size(max = 2048) String link) {}
