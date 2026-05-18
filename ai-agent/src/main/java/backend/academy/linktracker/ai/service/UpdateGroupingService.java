package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Выполняет оконную группировку обработанных обновлений по tgChatId.
 */
@Service
public class UpdateGroupingService {

    private final long windowMs;
    private final Clock clock;
    private final GroupedMessageAssembler groupedMessageAssembler;
    private final Map<Long, ChatBucket> bucketsByChatId = new HashMap<>();

    @Autowired
    public UpdateGroupingService(AiAgentProperties aiAgentProperties, GroupedMessageAssembler groupedMessageAssembler) {
        this(aiAgentProperties.getGrouping().getWindowMs(), Clock.systemUTC(), groupedMessageAssembler);
    }

    UpdateGroupingService(long windowMs, Clock clock) {
        this(windowMs, clock, new GroupedMessageAssembler());
    }

    UpdateGroupingService(long windowMs, Clock clock, GroupedMessageAssembler groupedMessageAssembler) {
        this.windowMs = windowMs;
        this.clock = clock;
        this.groupedMessageAssembler = Objects.requireNonNull(groupedMessageAssembler, "groupedMessageAssembler");
    }

    /**
     * Добавляет обновление в окно группировки и возвращает сообщения, готовые к публикации.
     */
    public synchronized List<GroupedMessage> accept(String messageKey, ProcessedLinkUpdate processedUpdate) {
        Objects.requireNonNull(processedUpdate, "processedUpdate");
        List<GroupedMessage> readyToPublish = new ArrayList<>();
        for (Long chatId : processedUpdate.tgChatIds()) {
            readyToPublish.addAll(acceptForChat(chatId, messageKey, processedUpdate));
        }
        return readyToPublish;
    }

    /**
     * Сбрасывает окна, срок которых истек, и возвращает итоговые сообщения.
     */
    public synchronized List<GroupedMessage> flushExpired() {
        long nowMs = Instant.now(clock).toEpochMilli();
        List<GroupedMessage> readyToPublish = new ArrayList<>();
        Iterator<Map.Entry<Long, ChatBucket>> iterator =
                bucketsByChatId.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, ChatBucket> bucketEntry = iterator.next();
            if (nowMs - bucketEntry.getValue().windowStartMs >= windowMs) {
                readyToPublish.add(
                        groupedMessageAssembler.assemble(bucketEntry.getKey(), bucketEntry.getValue().entries));
                iterator.remove();
            }
        }
        return readyToPublish;
    }

    private List<GroupedMessage> acceptForChat(Long chatId, String messageKey, ProcessedLinkUpdate processedUpdate) {
        long nowMs = Instant.now(clock).toEpochMilli();
        ChatBucket currentBucket = bucketsByChatId.get(chatId);
        if (currentBucket == null) {
            bucketsByChatId.put(chatId, ChatBucket.withEntry(nowMs, messageKey, processedUpdate));
            return List.of();
        }

        if (nowMs - currentBucket.windowStartMs < windowMs) {
            currentBucket.entries.add(new BucketEntry(messageKey, processedUpdate));
            return List.of();
        }

        GroupedMessage expiredBucketMessage = groupedMessageAssembler.assemble(chatId, currentBucket.entries);
        bucketsByChatId.put(chatId, ChatBucket.withEntry(nowMs, messageKey, processedUpdate));
        return List.of(expiredBucketMessage);
    }

    private static final class ChatBucket {
        private final long windowStartMs;
        private final List<BucketEntry> entries;

        private ChatBucket(long windowStartMs, List<BucketEntry> entries) {
            this.windowStartMs = windowStartMs;
            this.entries = entries;
        }

        private static ChatBucket withEntry(
                long windowStartMs, String messageKey, ProcessedLinkUpdate processedUpdate) {
            List<BucketEntry> entries = new ArrayList<>();
            entries.add(new BucketEntry(messageKey, processedUpdate));
            return new ChatBucket(windowStartMs, entries);
        }
    }

    record BucketEntry(String messageKey, ProcessedLinkUpdate processedUpdate) {}
}
