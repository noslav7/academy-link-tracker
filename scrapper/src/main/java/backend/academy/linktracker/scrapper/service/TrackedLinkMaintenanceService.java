package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Сервис фонового чтения и обновления отслеживаемых ссылок.
 */
@Service
public class TrackedLinkMaintenanceService {

    private final TrackedLinkRepository repository;
    private final SchedulerProperties schedulerProperties;

    public TrackedLinkMaintenanceService(TrackedLinkRepository repository, SchedulerProperties schedulerProperties) {
        this.repository = repository;
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * Возвращает один батч ссылок из БД для обработки планировщиком.
     *
     * @param offset смещение от начала выборки
     * @return список ссылок размером не более {@code batchSize}
     */
    public List<TrackedLink> fetchBatch(int offset) {
        return repository.findAllTrackedLinks(schedulerProperties.getBatchSize(), offset);
    }

    /**
     * Обновляет сохраненные поля отслеживаемой ссылки.
     *
     * @param trackedLink ссылка с обновленными метаданными
     */
    public void updateTrackedLink(TrackedLink trackedLink) {
        repository.updateTrackedLink(trackedLink);
    }
}
