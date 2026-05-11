package backend.academy.linktracker.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO входящего обновления из топика link.raw-updates, нужен как единый контракт сырого события.
 *
 * @param id идентификатор обновления для трассировки события между этапами
 * @param description полный текст обновления для фильтрации и возможной суммаризации
 * @param author автор обновления для правил исключения источников
 * @param tgChatIds получатели уведомления для последующей доставки
 */
public record RawLinkUpdate(
        @PositiveOrZero long id,
        @NotNull @Size(max = 16_384) String description,
        @Size(max = 512) String author,
        @NotEmpty List<@NotNull @Positive Long> tgChatIds) {}
