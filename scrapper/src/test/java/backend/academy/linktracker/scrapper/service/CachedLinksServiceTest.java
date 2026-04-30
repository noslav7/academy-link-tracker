package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
class CachedLinksServiceTest {

    @Test
    void givenLinksFromDomainServiceWhenListCalledThenResponseContainsExpectedSizeAndItems() {
        // Проверяет контракт ответа: list возвращает DTO, полностью соответствующий данным доменного сервиса.
        LinkService linkService = mock(LinkService.class);
        long chatId = 11L;
        List<LinkResponse> links = List.of(
                new LinkResponse(1L, "https://example.com/1", List.of("tag1"), List.of("filter1")),
                new LinkResponse(2L, "https://example.com/2", List.of(), List.of()));
        when(linkService.list(chatId)).thenReturn(links);

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        var response = cachedLinksService.list(chatId);
        var expectedResponse = new ListLinksResponse(links, links.size());

        assertEquals(expectedResponse, response);
    }

    @Test
    @Tag("sanity")
    void givenChatIdWhenListCalledThenDelegatesToDomainServiceWithSameChatId() {
        // Проверяет делегирование: list обращается к доменному сервису с тем же chatId.
        LinkService linkService = mock(LinkService.class);
        long chatId = 111L;
        when(linkService.list(chatId)).thenReturn(List.of());

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        cachedLinksService.list(chatId);

        verify(linkService).list(chatId);
    }

    @Test
    void givenEmptyLinksFromDomainServiceWhenListCalledThenResponseContainsEmptyItemsAndZeroSize() {
        // Проверяет границу: пустой доменный список маппится в пустой response.
        LinkService linkService = mock(LinkService.class);
        long chatId = 14L;
        List<LinkResponse> links = List.of();
        when(linkService.list(chatId)).thenReturn(links);

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        var response = cachedLinksService.list(chatId);
        var expectedResponse = new ListLinksResponse(links, 0);

        assertEquals(expectedResponse, response);
    }

    @Test
    void givenAddRequestWhenAddCalledThenReturnsDomainServiceResponse() {
        // Проверяет контракт ответа: add возвращает ровно тот объект, что вернул доменный сервис.
        LinkService linkService = mock(LinkService.class);
        long chatId = 12L;
        AddLinkRequest request = new AddLinkRequest("https://example.com", List.of("tag"), List.of("filter"));
        LinkResponse expected = new LinkResponse(3L, request.link(), request.tags(), request.filters());
        when(linkService.add(chatId, request)).thenReturn(expected);

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        LinkResponse actual = cachedLinksService.add(chatId, request);

        assertEquals(expected, actual);
    }

    @Test
    void givenAddRequestWhenAddCalledThenDelegatesToDomainService() {
        // Проверяет делегирование: add вызывает доменный сервис с теми же аргументами.
        LinkService linkService = mock(LinkService.class);
        long chatId = 112L;
        AddLinkRequest request = new AddLinkRequest("https://example.com/delegate", List.of("tag"), List.of());
        when(linkService.add(chatId, request))
                .thenReturn(new LinkResponse(30L, request.link(), request.tags(), request.filters()));

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        cachedLinksService.add(chatId, request);

        verify(linkService).add(chatId, request);
    }

    @Test
    void givenLinkWhenRemoveCalledThenReturnsDomainServiceResponse() {
        // Проверяет контракт ответа: remove возвращает ровно тот объект, что вернул доменный сервис.
        LinkService linkService = mock(LinkService.class);
        long chatId = 13L;
        String link = "https://example.com/remove";
        LinkResponse expected = new LinkResponse(4L, link, List.of(), List.of());
        when(linkService.remove(chatId, link)).thenReturn(expected);

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        LinkResponse actual = cachedLinksService.remove(chatId, link);

        assertEquals(expected, actual);
    }

    @Test
    void givenLinkWhenRemoveCalledThenDelegatesToDomainService() {
        // Проверяет делегирование: remove вызывает доменный сервис с теми же аргументами.
        LinkService linkService = mock(LinkService.class);
        long chatId = 113L;
        String link = "https://example.com/remove-delegate";
        when(linkService.remove(chatId, link)).thenReturn(new LinkResponse(40L, link, List.of(), List.of()));

        CachedLinksService cachedLinksService = new CachedLinksService(linkService);
        cachedLinksService.remove(chatId, link);

        verify(linkService).remove(chatId, link);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("domainFailureCases")
    void givenDomainServiceThrowsWhenOperationCalledThenExceptionIsPropagated(FailureCase failureCase) {
        // Проверяет ошибочный контракт: фасад не скрывает исключения доменного сервиса для разных операций.
        LinkService linkService = mock(LinkService.class);
        RuntimeException expected = failureCase.stubDomainFailure(linkService);
        CachedLinksService cachedLinksService = new CachedLinksService(linkService);

        RuntimeException actual = assertThrows(RuntimeException.class, () -> failureCase.invoke(cachedLinksService));

        assertSame(expected, actual);
        failureCase.verifyDelegation(linkService);
    }

    private static List<FailureCase> domainFailureCases() {
        long listChatId = 15L;
        long addChatId = 16L;
        long removeChatId = 17L;
        AddLinkRequest addRequest = new AddLinkRequest("https://example.com/error", List.of(), List.of());
        String removeLink = "https://example.com/error-remove";

        return List.of(
                new FailureCase(
                        "givenDomainServiceThrowsWhenListCalledThenExceptionIsPropagated",
                        linkService -> {
                            RuntimeException expected = new RuntimeException("list failed");
                            when(linkService.list(listChatId)).thenThrow(expected);
                            return expected;
                        },
                        cachedLinksService -> cachedLinksService.list(listChatId),
                        linkService -> verify(linkService).list(listChatId)),
                new FailureCase(
                        "givenDomainServiceThrowsWhenAddCalledThenExceptionIsPropagated",
                        linkService -> {
                            RuntimeException expected = new RuntimeException("add failed");
                            when(linkService.add(addChatId, addRequest)).thenThrow(expected);
                            return expected;
                        },
                        cachedLinksService -> cachedLinksService.add(addChatId, addRequest),
                        linkService -> verify(linkService).add(addChatId, addRequest)),
                new FailureCase(
                        "givenDomainServiceThrowsWhenRemoveCalledThenExceptionIsPropagated",
                        linkService -> {
                            RuntimeException expected = new RuntimeException("remove failed");
                            when(linkService.remove(removeChatId, removeLink)).thenThrow(expected);
                            return expected;
                        },
                        cachedLinksService -> cachedLinksService.remove(removeChatId, removeLink),
                        linkService -> verify(linkService).remove(removeChatId, removeLink)));
    }

    private record FailureCase(
            String testCaseName,
            DomainFailureStubber stubber,
            CachedServiceInvocation invocation,
            LinkServiceVerifier verification) {

        RuntimeException stubDomainFailure(LinkService linkService) {
            return stubber.stub(linkService);
        }

        void invoke(CachedLinksService cachedLinksService) {
            invocation.invoke(cachedLinksService);
        }

        void verifyDelegation(LinkService linkService) {
            verification.verify(linkService);
        }

        @Override
        public String toString() {
            return testCaseName;
        }
    }

    @FunctionalInterface
    private interface DomainFailureStubber {
        RuntimeException stub(LinkService linkService);
    }

    @FunctionalInterface
    private interface CachedServiceInvocation {
        void invoke(CachedLinksService cachedLinksService);
    }

    @FunctionalInterface
    private interface LinkServiceVerifier {
        void verify(LinkService linkService);
    }
}
