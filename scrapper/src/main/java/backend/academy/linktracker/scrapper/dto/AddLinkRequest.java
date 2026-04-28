package backend.academy.linktracker.scrapper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO запроса на добавление ссылки в отслеживание.
 *
 * @param link URL ссылки для отслеживания
 * @param tags список пользовательских тегов
 * @param filters список фильтров
 */
public record AddLinkRequest(
        @NotBlank @Size(max = 2048) String link,
        List<@NotBlank @Size(max = 128) String> tags,
        List<@NotBlank @Size(max = 256) String> filters) {}
