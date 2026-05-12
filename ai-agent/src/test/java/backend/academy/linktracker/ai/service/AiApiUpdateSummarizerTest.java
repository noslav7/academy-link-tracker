package backend.academy.linktracker.ai.service;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@Tag("unit")
class AiApiUpdateSummarizerTest {

    private static final String MODEL_PATH = "/models/test-summarizer";
    private static final String API_TOKEN = "hf-test-token";
    private static final String PROMPT = "Summarize the following update in 2-3 sentences.";

    private WireMockServer wireMockServer;
    private AiAgentProperties properties;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        properties = new AiAgentProperties();
        properties.getSummarization().setApiUrl(wireMockServer.baseUrl() + MODEL_PATH);
        properties.getSummarization().setApiToken(API_TOKEN);
        properties.getSummarization().setPrompt(PROMPT);
        properties.getSummarization().setTimeout(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void givenLongUpdateWhenSummarizeThenSendsPromptToAiApiAndReturnsSummaryText() {
        // Проверяет реальный HTTP-контракт: prompt + текст уходят во внешний AI API.
        wireMockServer.stubFor(post(urlEqualTo(MODEL_PATH))
                .withHeader("Authorization", equalTo("Bearer " + API_TOKEN))
                .withRequestBody(matchingJsonPath("$.inputs", containing(PROMPT)))
                .withRequestBody(matchingJsonPath("$.inputs", containing("Long update with details")))
                .withRequestBody(matchingJsonPath("$.options.wait_for_model"))
                .willReturn(okJson("""
                        [
                          {
                            "summary_text": "Short AI summary"
                          }
                        ]
                        """)));
        AiApiUpdateSummarizer summarizer =
                new AiApiUpdateSummarizer(RestClient.builder().build(), properties);

        String summary = summarizer.summarize("Long update with details");

        assertEquals("Short AI summary", summary);
    }

    @Test
    void givenGeneratedTextResponseWhenSummarizeThenReturnsGeneratedText() {
        // Некоторые inference-модели возвращают generated_text вместо summary_text.
        wireMockServer.stubFor(post(urlEqualTo(MODEL_PATH)).willReturn(okJson("""
                        {
                          "generated_text": "Generated AI summary"
                        }
                        """)));
        AiApiUpdateSummarizer summarizer =
                new AiApiUpdateSummarizer(RestClient.builder().build(), properties);

        String summary = summarizer.summarize("Long update with details");

        assertEquals("Generated AI summary", summary);
    }

    @Test
    void givenAiApiErrorResponseWhenSummarizeThenThrowsException() {
        wireMockServer.stubFor(post(urlEqualTo(MODEL_PATH)).willReturn(okJson("""
                        {
                          "error": "Model is loading"
                        }
                        """)));
        AiApiUpdateSummarizer summarizer =
                new AiApiUpdateSummarizer(RestClient.builder().build(), properties);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> summarizer.summarize("Long update with details"));

        assertEquals("AI API returned an error: Model is loading", exception.getMessage());
    }
}
