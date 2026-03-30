package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Планировщик периодической проверки обновлений отслеживаемых ссылок.
 * Загружает ссылки пакетами и обрабатывает многопоточно.
 * Бизнес-логика проверки одной ссылки делегирована {@link LinkUpdateChecker}.
 */
@Component
public class LinkUpdatesScheduler {

    /**
     * Логгер планировщика.
     */
    private static final Logger log = LoggerFactory.getLogger(LinkUpdatesScheduler.class);

    /**
     * Сервис пакетной загрузки и обновления ссылок.
     */
    private final TrackedLinkMaintenanceService maintenanceService;
    /**
     * Компонент проверки одной ссылки на наличие обновлений.
     */
    private final LinkUpdateChecker linkUpdateChecker;
    /**
     * Компонент публикации итогов прогона планировщика.
     */
    private final SchedulerRunReporter runReporter;
    /**
     * Параметры запуска планировщика.
     */
    private final SchedulerProperties schedulerProperties;
    /**
     * Пул потоков для параллельной обработки ссылок.
     */
    private final ExecutorService executorService;

    /**
     * Создает планировщик и инициализирует пул потоков для параллельной обработки.
     *
     * @param maintenanceService сервис чтения/обновления ссылок в БД
     * @param linkUpdateChecker  компонент проверки одной ссылки на обновление
     * @param runReporter        компонент формирования и отправки итогового отчета запуска
     * @param schedulerProperties настройки планировщика
     */
    @Autowired
    public LinkUpdatesScheduler(
            TrackedLinkMaintenanceService maintenanceService,
            LinkUpdateChecker linkUpdateChecker,
            SchedulerRunReporter runReporter,
            SchedulerProperties schedulerProperties) {
        this.maintenanceService = maintenanceService;
        this.linkUpdateChecker = linkUpdateChecker;
        this.runReporter = runReporter;
        this.schedulerProperties = schedulerProperties;
        this.executorService = Executors.newFixedThreadPool(schedulerProperties.getThreadCount());
    }

    /**
     * Останавливает пул потоков при завершении приложения.
     */
    @PreDestroy
    private void shutdownPool() {
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
        List<SchedulerFailedLink> failedLinks = Collections.synchronizedList(new ArrayList<>());

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

        runReporter.report(runId, processedCount.get(), successCount.get(), failedLinks);
    }

    /**
     * Обрабатывает текущий пакет ссылок параллельно в пределах заданного числа потоков.
     *
     * @param batch пакет ссылок
     * @param processedCount счётчик обработанных ссылок
     * @param successCount счётчик успешно обработанных ссылок
     * @param failedLinks список ссылок с ошибками обработки
     */
    private void processBatchInParallel(
            List<TrackedLink> batch,
            AtomicInteger processedCount,
            AtomicInteger successCount,
            List<SchedulerFailedLink> failedLinks) {

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
                        failedLinks.add(new SchedulerFailedLink(link.url(), link.chatId(), ex.getMessage()));
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

    /**
     * Разбивает список на ограниченное число непрерывных подсписков.
     *
     * @param list исходный список
     * @param partitions желаемое число частей
     * @param <T> тип элементов списка
     * @return список подсписков для параллельной обработки
     */
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
}
