package backend.academy.linktracker.bot.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO входящего уведомления об обновлении ссылки.
 *
 * @param id идентификатор ссылки
 * @param url URL ссылки
 * @param description описание обновления
 * @param tgChatIds получатели уведомления
 */
public record LinkUpdate(
        @Positive long id,
        @NotBlank @Size(max = 2048) String url,
        @NotBlank @Size(max = 4000) String description,
        @NotEmpty List<@NotNull @Positive Long> tgChatIds) {}
