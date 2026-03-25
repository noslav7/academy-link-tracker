package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Планировщик периодической проверки обновлений отслеживаемых ссылок.
 * Загружает ссылки пакетами и обрабатывает многопоточно.
 * Бизнес-логика проверки одной ссылки делегирована {@link LinkUpdateChecker}.
 */
@Component
public class LinkUpdatesScheduler {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdatesScheduler.class);

    private final TrackedLinkMaintenanceService maintenanceService;
    private final LinkUpdateChecker linkUpdateChecker;
    private final MessageSender messageSender;
    private final SchedulerProperties schedulerProperties;
    private final ExecutorService executorService;

    /**
     * Создает планировщик и инициализирует пул потоков для параллельной обработки.
     *
     * @param maintenanceService сервис чтения/обновления ссылок в БД
     * @param linkUpdateChecker  компонент проверки одной ссылки на обновление
     * @param messageSender      отправитель уведомлений (для error-отчётов)
     * @param schedulerProperties настройки планировщика
     */
    public LinkUpdatesScheduler(
            TrackedLinkMaintenanceService maintenanceService,
            LinkUpdateChecker linkUpdateChecker,
            MessageSender messageSender,
            SchedulerProperties schedulerProperties) {
        this.maintenanceService = maintenanceService;
        this.linkUpdateChecker = linkUpdateChecker;
        this.messageSender = messageSender;
        this.schedulerProperties = schedulerProperties;
        this.executorService = Executors.newFixedThreadPool(schedulerProperties.getThreadCount());
    }

    /**
     * Останавливает пул потоков при завершении приложения.
     */
    @PreDestroy
    void shutdownPool() {
        executorService.shutdownNow();
    }

    /**
     * Основной цикл: загружает ссылки пакетами, проверяет обновления, формирует отчёт.
     */
    @Scheduled(fixedDelayString = "#{@schedulerProperties.interval.toMillis()}")
    public void checkUpdates() {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        AtomicInteger processedCount = new AtomicInteger();
        AtomicInteger successCount = new AtomicInteger();
        List<FailedLink> failedLinks = Collections.synchronizedList(new ArrayList<>());

        int offset = 0;
        while (true) {
            List<TrackedLink> batch = maintenanceService.fetchBatch(offset);
            if (batch.isEmpty()) {
                break;
            }

            processBatchInParallel(batch, processedCount, successCount, failedLinks);

            if (batch.size() < schedulerProperties.getBatchSize()) {
                break;
            }
            offset += batch.size();
        }

        logRunReport(runId, processedCount.get(), successCount.get(), failedLinks);

        if (!failedLinks.isEmpty()) {
            sendErrorNotification(runId, processedCount.get(), successCount.get(), failedLinks);
        }
    }

    private void processBatchInParallel(
            List<TrackedLink> batch,
            AtomicInteger processedCount,
            AtomicInteger successCount,
            List<FailedLink> failedLinks) {

        int threadCount = schedulerProperties.getThreadCount();
        List<List<TrackedLink>> chunks = partitionList(batch, threadCount);

        List<Future<?>> futures = new ArrayList<>();
        for (List<TrackedLink> chunk : chunks) {
            futures.add(executorService.submit(() -> {
                for (TrackedLink link : chunk) {
                    processedCount.incrementAndGet();
                    try {
                        linkUpdateChecker.check(link);
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        failedLinks.add(new FailedLink(link.url(), link.chatId(), ex.getMessage()));
                        log.atWarn()
                                .addKeyValue("url", link.url())
                                .addKeyValue("chatId", link.chatId())
                                .addKeyValue("error", ex.getMessage())
                                .log("Failed to process link");
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ex) {
                log.atError().addKeyValue("error", ex.getMessage()).log("Unexpected error in batch processing thread");
            }
        }
    }

    private void logRunReport(String runId, int processed, int success, List<FailedLink> failed) {
        log.atInfo()
                .addKeyValue("runId", runId)
                .addKeyValue("processedCount", processed)
                .addKeyValue("successCount", success)
                .addKeyValue("failedCount", failed.size())
                .log("Scheduler run completed");

        for (FailedLink fl : failed) {
            log.atWarn()
                    .addKeyValue("runId", runId)
                    .addKeyValue("url", fl.url())
                    .addKeyValue("chatId", fl.chatId())
                    .addKeyValue("reason", fl.reason())
                    .log("Failed link in run report");
        }
    }

    private void sendErrorNotification(String runId, int processed, int success, List<FailedLink> failedLinks) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠ Отчёт планировщика (runId: %s)\n".formatted(runId));
        sb.append("Обработано: %d, успешно: %d, с ошибками: %d\n\n".formatted(processed, success, failedLinks.size()));
        sb.append("Неуспешные ссылки:\n");
        for (FailedLink fl : failedLinks) {
            sb.append("• %s — %s\n".formatted(fl.url(), fl.reason()));
        }

        List<Long> affectedChatIds =
                failedLinks.stream().map(FailedLink::chatId).distinct().toList();

        messageSender.send(new LinkUpdate(0, "system://scheduler-error-report", sb.toString(), affectedChatIds));
    }

    private static <T> List<List<T>> partitionList(List<T> list, int partitions) {
        if (list.isEmpty() || partitions <= 0) {
            return List.of();
        }
        int effectivePartitions = Math.min(partitions, list.size());
        int chunkSize = (list.size() + effectivePartitions - 1) / effectivePartitions;
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            result.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return result;
    }

    private record FailedLink(String url, long chatId, String reason) {}
}
