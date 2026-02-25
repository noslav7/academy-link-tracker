package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.GithubClient;
import backend.academy.linktracker.scrapper.client.StackoverflowClient;
import backend.academy.linktracker.scrapper.exception.UnsupportedLinkException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LinkMetadataResolver {

    private final GithubClient githubClient;
    private final StackoverflowClient stackoverflowClient;

    public LinkMetadataResolver(GithubClient githubClient, StackoverflowClient stackoverflowClient) {
        this.githubClient = githubClient;
        this.stackoverflowClient = stackoverflowClient;
    }

    public Instant resolveLastUpdated(String link) {
        URI uri = URI.create(link);
        String host = uri.getHost();
        List<String> segments = pathSegments(uri.getPath());

        if (host != null && host.contains("github.com") && segments.size() >= 2) {
            return githubClient.fetchRepositoryUpdatedAt(segments.get(0), segments.get(1));
        }
        if (host != null && host.contains("stackoverflow.com")) {
            Long questionId = extractStackoverflowQuestionId(segments);
            if (questionId != null) {
                return stackoverflowClient.fetchQuestionUpdatedAt(questionId);
            }
        }
        throw new UnsupportedLinkException(link);
    }

    private static List<String> pathSegments(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return List.of(path.split("/")).stream()
                .map(String::trim)
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    private static Long extractStackoverflowQuestionId(List<String> segments) {
        if (segments.size() < 2) {
            return null;
        }
        if ("questions".equals(segments.get(0)) || "q".equals(segments.get(0))) {
            try {
                return Long.parseLong(segments.get(1));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
