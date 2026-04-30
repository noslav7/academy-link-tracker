package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class CachedLinksServiceTest {

    @Test
    void givenLinksFromDomainServiceWhenListCalledThenResponseContainsExpectedSizeAndItems() {
        // Проверяет фасад: response /list строится из доменного списка и корректно считает size.
        LinkService linkService = mock(LinkService.class);
        long chatId = 11L;
        List<LinkResponse> links = List.of(
                new LinkResponse(1L, "https://example.com/1", List.of("tag1"), List.of("filter1")),
                new LinkResponse(2L, "https://example.com/2", List.of(), List.of()));
        when(linkService.list(chatId)).thenReturn(links);

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        var response = cachedLinksService.list(chatId);

        assertAll(() -> assertEquals(links, response.links()), () -> assertEquals(2, response.size()));
        verify(linkService).list(chatId);
    }

    @Test
    void givenAddRequestWhenAddCalledThenDelegatesToDomainService() {
        // Проверяет фасад: add делегируется доменному сервису без изменения контракта.
        LinkService linkService = mock(LinkService.class);
        long chatId = 12L;
        AddLinkRequest request = new AddLinkRequest("https://example.com", List.of("tag"), List.of("filter"));
        LinkResponse expected = new LinkResponse(3L, request.link(), request.tags(), request.filters());
        when(linkService.add(chatId, request)).thenReturn(expected);

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        LinkResponse actual = cachedLinksService.add(chatId, request);

        assertEquals(expected, actual);
        verify(linkService).add(chatId, request);
    }

    @Test
    void givenLinkWhenRemoveCalledThenDelegatesToDomainService() {
        // Проверяет фасад: remove делегируется доменному сервису без изменения контракта.
        LinkService linkService = mock(LinkService.class);
        long chatId = 13L;
        String link = "https://example.com/remove";
        LinkResponse expected = new LinkResponse(4L, link, List.of(), List.of());
        when(linkService.remove(chatId, link)).thenReturn(expected);

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        LinkResponse actual = cachedLinksService.remove(chatId, link);

        assertEquals(expected, actual);
        verify(linkService).remove(chatId, link);
    }
}
