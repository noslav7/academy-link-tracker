package backend.academy.linktracker.scrapper.metrics;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

/**
 * Сервис пересчета gauge links_on_track_total.
 */
@Service
@DependsOn("databaseMigrationRunner")
public class TrackedLinksMetricsService {
    private static final int PAGE_SIZE = 500;
    private static final String TRACKED_SOURCE_GITHUB = "github";
    private static final String TRACKED_SOURCE_STACKOVERFLOW = "stackoverflow";

    private final TrackedLinkRepository trackedLinkRepository;
    private final ScrapperMetricsRecorder metricsRecorder;

    public TrackedLinksMetricsService(
            TrackedLinkRepository trackedLinkRepository, ScrapperMetricsRecorder metricsRecorder) {
        this.trackedLinkRepository = trackedLinkRepository;
        this.metricsRecorder = metricsRecorder;
        refreshGaugeInternal();
    }

    public void refreshTrackedLinksGauge() {
        refreshGaugeInternal();
    }

    private void refreshGaugeInternal() {
        long githubCount = 0L;
        long stackoverflowCount = 0L;
        int offset = 0;
        while (true) {
            List<TrackedLink> links = trackedLinkRepository.findAllTrackedLinks(PAGE_SIZE, offset);
            if (links.isEmpty()) {
                break;
            }
            for (TrackedLink link : links) {
                String source = resolveTrackedSource(link.url());
                if (TRACKED_SOURCE_GITHUB.equals(source)) {
                    githubCount++;
                    continue;
                }
                if (TRACKED_SOURCE_STACKOVERFLOW.equals(source)) {
                    stackoverflowCount++;
                }
            }
            if (links.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
        metricsRecorder.setTrackedLinksCount(TRACKED_SOURCE_GITHUB, githubCount);
        metricsRecorder.setTrackedLinksCount(TRACKED_SOURCE_STACKOVERFLOW, stackoverflowCount);
    }

    private static String resolveTrackedSource(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return "";
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.contains("github.com")) {
                return TRACKED_SOURCE_GITHUB;
            }
            if (normalizedHost.contains("stackoverflow.com")) {
                return TRACKED_SOURCE_STACKOVERFLOW;
            }
            return "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}
