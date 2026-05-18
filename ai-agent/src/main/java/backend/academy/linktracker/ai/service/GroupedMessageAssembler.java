package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Формирует итоговое сообщение группировки из накопленного bucket.
 */
@Component
public class GroupedMessageAssembler {

    /**
     * Собирает сообщение для одного chatId из записей окна группировки.
     *
     * @param chatId идентификатор чата, для которого формируется результат
     * @param bucketEntries накопленные записи окна
     * @return сообщение, готовое к публикации в Kafka
     */
    public GroupedMessage assemble(long chatId, List<UpdateGroupingService.BucketEntry> bucketEntries) {
        if (bucketEntries.size() == 1) {
            UpdateGroupingService.BucketEntry onlyEntry = bucketEntries.getFirst();
            ProcessedLinkUpdate source = onlyEntry.processedUpdate();
            ProcessedLinkUpdate passthrough =
                    new ProcessedLinkUpdate(source.id(), source.description(), List.of(chatId), source.priority());
            return new GroupedMessage(onlyEntry.messageKey(), passthrough);
        }

        UpdateGroupingService.BucketEntry firstEntry = bucketEntries.getFirst();
        long groupedId = firstEntry.processedUpdate().id();
        UpdatePriority groupedPriority = firstEntry.processedUpdate().priority();
        StringBuilder groupedDescription = new StringBuilder();
        for (int index = 0; index < bucketEntries.size(); index++) {
            UpdateGroupingService.BucketEntry bucketEntry = bucketEntries.get(index);
            groupedPriority = UpdatePriority.max(
                    groupedPriority, bucketEntry.processedUpdate().priority());
            groupedDescription
                    .append(index + 1)
                    .append(". ")
                    .append(bucketEntry.processedUpdate().description());
            if (index + 1 < bucketEntries.size()) {
                groupedDescription.append(System.lineSeparator());
            }
        }

        ProcessedLinkUpdate groupedUpdate =
                new ProcessedLinkUpdate(groupedId, groupedDescription.toString(), List.of(chatId), groupedPriority);
        return new GroupedMessage(resolveGroupedMessageKey(chatId, bucketEntries), groupedUpdate);
    }

    private static String resolveGroupedMessageKey(long chatId, List<UpdateGroupingService.BucketEntry> bucketEntries) {
        for (UpdateGroupingService.BucketEntry entry : bucketEntries) {
            if (hasText(entry.messageKey())) {
                return entry.messageKey();
            }
        }
        return Long.toString(chatId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
