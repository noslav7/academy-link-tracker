package backend.academy.linktracker.scrapper.client;

import java.time.Instant;
import java.util.Optional;

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

    /**
     * Возвращает сведения о последнем созданном Issue или Pull Request.
     *
     * @param owner владелец репозитория
     * @param repository имя репозитория
     * @return данные последнего события, если удалось получить
     */
    default Optional<GithubIssueOrPullRequestUpdate> fetchLatestIssueOrPullRequest(String owner, String repository) {
        return Optional.empty();
    }

    /**
     * Детали обновления GitHub по последнему Issue/PR.
     *
     * @param issue тип события (issue или pull request)
     * @param title заголовок issue/pr
     * @param authorLogin логин автора
     * @param createdAt время создания
     * @param body описание issue/pr
     */
    record GithubIssueOrPullRequestUpdate(
            boolean issue, String title, String authorLogin, Instant createdAt, String body) {}
}
