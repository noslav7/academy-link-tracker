package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.GithubClient;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Резолвер метаданных для ссылок на репозитории GitHub.
 */
@Component
public class GithubLinkMetadataResolver extends AbstractHostBasedLinkMetadataResolver {

    /**
     * Часть имени хоста, указывающая на GitHub.
     */
    private static final String GITHUB_HOST_PART = "github.com";
    /**
     * Индекс сегмента owner в пути ссылки.
     */
    private static final int OWNER_SEGMENT_INDEX = 0;
    /**
     * Индекс сегмента repository в пути ссылки.
     */
    private static final int REPOSITORY_SEGMENT_INDEX = 1;
    /**
     * Минимальное количество сегментов пути для ссылки вида /owner/repository.
     */
    private static final int MIN_SEGMENTS_COUNT = 2;

    /**
     * Клиент GitHub API.
     */
    private final GithubClient githubClient;

    /**
     * Создает резолвер GitHub-ссылок.
     *
     * @param githubClient клиент доступа к GitHub API
     */
    public GithubLinkMetadataResolver(GithubClient githubClient) {
        super(GITHUB_HOST_PART, MIN_SEGMENTS_COUNT);
        this.githubClient = githubClient;
    }

    @Override
    public Instant resolveLastUpdated(URI uri) {
        List<String> segments = extractPathSegments(uri);
        return githubClient.fetchRepositoryUpdatedAt(
                segments.get(OWNER_SEGMENT_INDEX), segments.get(REPOSITORY_SEGMENT_INDEX));
    }

    @Override
    public String describeUpdate(URI uri) {
        List<String> segments = extractPathSegments(uri);
        String owner = segments.get(OWNER_SEGMENT_INDEX);
        String repo = segments.get(REPOSITORY_SEGMENT_INDEX);
        return "Обновление в репозитории %s/%s (возможно: новый PR или Issue)".formatted(owner, repo);
    }
}
