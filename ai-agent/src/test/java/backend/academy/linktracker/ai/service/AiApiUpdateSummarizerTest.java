package backend.academy.linktracker.ai.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import backend.academy.linktracker.ai.properties.AiAgentProperties;
import backend.academy.linktracker.ai.service.exception.AiSummarizationBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class AiApiUpdateSummarizerTest {

    private static final String API_URL = "http://hf.test/models/test-summarizer";
    private static final String API_TOKEN = "hf-test-token";
    private static final String PROMPT = "Summarize the following update in 2-3 sentences.";

    private AiAgentProperties properties;
    private MockRestServiceServer mockServer;
    private AiApiUpdateSummarizer summarizer;

    @BeforeEach
    void setUp() {
        properties = new AiAgentProperties();
        properties.getSummarization().setApiUrl(API_URL);
        properties.getSummarization().setApiToken(API_TOKEN);
        properties.getSummarization().setPrompt(PROMPT);
        properties.getSummarization().setTimeout(Duration.ofSeconds(2));

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        AiApiSummarizationResponseParser parser = new AiApiSummarizationResponseParser(new ObjectMapper());
        summarizer = new AiApiUpdateSummarizer(restClientBuilder.build(), parser, properties);
    }

    @Test
    @Tag("sanity")
    @DisplayName("Sanity: summarize happy-path with mocked API")
    void givenLongUpdateWhenSummarizeThenSendsPromptToAiApiAndReturnsSummaryText() {
        mockServer
                .expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.inputs", containsString(PROMPT)))
                .andExpect(jsonPath("$.inputs", containsString("Long update with details")))
                .andExpect(jsonPath("$.options.wait_for_model").value(true))
                .andRespond(withSuccess("""
                        [
                          {
                            "summary_text": "Short AI summary"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        String summary = summarizer.summarize("Long update with details");

        assertEquals("Short AI summary", summary);
        mockServer.verify();
    }

    @Test
    void givenGeneratedTextResponseWhenSummarizeThenReturnsGeneratedText() {
        mockServer
                .expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "generated_text": "Generated AI summary"
                        }
                        """, MediaType.APPLICATION_JSON));

        String summary = summarizer.summarize("Long update with details");

        assertEquals("Generated AI summary", summary);
        mockServer.verify();
    }

    @Test
    void givenAiApiErrorResponseWhenSummarizeThenThrowsException() {
        mockServer
                .expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "error": "Model is loading"
                        }
                        """, MediaType.APPLICATION_JSON));

        AiSummarizationBusinessException exception = assertThrows(
                AiSummarizationBusinessException.class, () -> summarizer.summarize("Long update with details"));

        assertEquals("AI API returned an error: Model is loading", exception.getMessage());
        mockServer.verify();
    }
}
