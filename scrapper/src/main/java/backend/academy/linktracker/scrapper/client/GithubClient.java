package backend.academy.linktracker.scrapper.client;

import java.time.Instant;

public interface GithubClient {
    Instant fetchRepositoryUpdatedAt(String owner, String repository);
}
