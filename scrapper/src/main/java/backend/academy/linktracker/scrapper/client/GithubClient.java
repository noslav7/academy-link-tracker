package backend.academy.linktracker.scrapper.client;

import java.time.Instant;

/**
 * Контракт клиента доступа к GitHub API.
 */
public interface GithubClient {
    /**
     * Возвращает время последнего обновления указанного репозитория.
     *
     * @param owner владелец репозитория
     * @param repository имя репозитория
     * @return время последнего обновления
     */
    Instant fetchRepositoryUpdatedAt(String owner, String repository);
}
