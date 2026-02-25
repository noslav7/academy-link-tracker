package backend.academy.linktracker.scrapper.client;

import java.time.Instant;

public interface StackoverflowClient {
    Instant fetchQuestionUpdatedAt(long questionId);
}
