package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.client.dto.AddLinkRequest;
import backend.academy.linktracker.bot.client.dto.LinkResponse;
import backend.academy.linktracker.bot.client.dto.ListLinksResponse;
import backend.academy.linktracker.bot.client.dto.RemoveLinkRequest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class RestScrapperClient implements ScrapperClient {

    private final RestClient restClient;

    public RestScrapperClient(RestClient scrapperRestClient) {
        this.restClient = scrapperRestClient;
    }

    @Override
    public void registerChat(long chatId) {
        try {
            restClient
                    .post()
                    .uri("/tg-chat/{id}", chatId)
                    .retrieve()
                    .onStatus(HttpStatus.CONFLICT::equals, (request, response) -> {})
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new ScrapperClientException("Failed to register chat in scrapper", ex);
        }
    }

    @Override
    public LinkResponse addLink(long chatId, String link, List<String> tags, List<String> filters) {
        try {
            return restClient
                    .post()
                    .uri("/links")
                    .header("Tg-Chat-Id", String.valueOf(chatId))
                    .body(new AddLinkRequest(link, tags, filters))
                    .retrieve()
                    .onStatus(HttpStatus.CONFLICT::equals, (request, response) -> {
                        throw new LinkAlreadyTrackedException();
                    })
                    .body(LinkResponse.class);
        } catch (LinkAlreadyTrackedException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ScrapperClientException("Failed to add link in scrapper", ex);
        }
    }

    @Override
    public LinkResponse removeLink(long chatId, String link) {
        try {
            return restClient
                    .method(HttpMethod.DELETE)
                    .uri("/links")
                    .header("Tg-Chat-Id", String.valueOf(chatId))
                    .body(new RemoveLinkRequest(link))
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, (request, response) -> {
                        throw new LinkNotFoundException();
                    })
                    .body(LinkResponse.class);
        } catch (LinkNotFoundException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ScrapperClientException("Failed to remove link in scrapper", ex);
        }
    }

    @Override
    public List<LinkResponse> getLinks(long chatId) {
        try {
            ListLinksResponse response = restClient
                    .get()
                    .uri("/links")
                    .header("Tg-Chat-Id", String.valueOf(chatId))
                    .retrieve()
                    .body(ListLinksResponse.class);
            return response == null || response.links() == null ? List.of() : response.links();
        } catch (RestClientException ex) {
            throw new ScrapperClientException("Failed to fetch links from scrapper", ex);
        }
    }
}
