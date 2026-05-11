package backend.academy.linktracker.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO обработанного обновления для топика link.processed-updates, нужен как контракт следующего этапа.
 *
 * @param id идентификатор обновления для сохранения связи с исходным событием
 * @param description обработанный текст обновления для доставки пользователю
 * @param tgChatIds получатели уведомления для fan-out на стороне доставки
 * @param priority приоритет для будущей маршрутизации и сортировки обработанных событий
 */
public record ProcessedLinkUpdate(
        @PositiveOrZero long id,
        @NotNull @Size(max = 16_384) String description,
        @NotEmpty List<@NotNull @Positive Long> tgChatIds,
        @NotNull UpdatePriority priority) {}
