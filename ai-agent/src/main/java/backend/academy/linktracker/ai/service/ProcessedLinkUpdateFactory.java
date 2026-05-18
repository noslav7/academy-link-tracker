package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.dto.RawLinkUpdate;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Создает результат обработки обновления для следующего этапа пайплайна.
 */
@Component
public class ProcessedLinkUpdateFactory {

    /**
     * Создает выходное обновление, чтобы контракт следующего этапа формировался в одном месте.
     *
     * @param update исходное входящее обновление
     * @param processedDescription описание после обработки
     * @param priority вычисленный приоритет обновления
     * @return DTO для следующего этапа пайплайна
     */
    public ProcessedLinkUpdate create(RawLinkUpdate update, String processedDescription, UpdatePriority priority) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(priority, "priority");
        return new ProcessedLinkUpdate(update.id(), processedDescription, update.tgChatIds(), priority);
    }
}
