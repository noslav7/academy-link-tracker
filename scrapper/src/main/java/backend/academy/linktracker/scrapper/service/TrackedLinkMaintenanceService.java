package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Сервис фонового чтения и обновления отслеживаемых ссылок.
 */
@Service
public class TrackedLinkMaintenanceService {

    /**
     * Размер страницы при фоновой выборке отслеживаемых ссылок.
     */
    private static final int TRACKED_LINKS_PAGE_SIZE = 200;

    /**
     * Репозиторий хранения отслеживаемых ссылок.
     */
    private final TrackedLinkRepository repository;

    /**
     * Создает сервис фонового обслуживания отслеживаемых ссылок.
     *
     * @param repository репозиторий хранения ссылок
     */
    public TrackedLinkMaintenanceService(TrackedLinkRepository repository) {
        this.repository = repository;
    }

    /**
     * Возвращает все отслеживаемые ссылки из всех чатов постранично.
     *
     * @return список всех отслеживаемых ссылок
     */
    public List<TrackedLink> allTrackedLinks() {
        List<TrackedLink> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<TrackedLink> page = repository.findAllTrackedLinks(TRACKED_LINKS_PAGE_SIZE, offset);
            if (page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < TRACKED_LINKS_PAGE_SIZE) {
                break;
            }
            offset += page.size();
        }
        return result;
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
