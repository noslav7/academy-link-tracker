package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

@Tag("unit")
class LinkServiceTest {

    @Test
    void givenConcurrentDuplicateInsertWhenAddCalledThenReturnsDomainConflict() {
        // Проверяет гонку find-then-insert: уникальный индекс БД должен маппиться в штатный конфликт API.
        TrackedLinkRepository repository = mock(TrackedLinkRepository.class);
        LinkMetadataResolver metadataResolver = mock(LinkMetadataResolver.class);
        ChatLinkAccessValidator accessValidator = mock(ChatLinkAccessValidator.class);
        LinkService linkService = new LinkService(repository, metadataResolver, accessValidator);
        long chatId = 42L;
        String link = "https://github.com/example/repo";
        AddLinkRequest request = new AddLinkRequest(link, List.of(), List.of());
        Instant updatedAt = Instant.parse("2026-05-03T00:00:00Z");
        DuplicateKeyException duplicateKeyException = new DuplicateKeyException("duplicate tracked link");
        when(repository.findByChatIdAndUrl(chatId, link)).thenReturn(Optional.empty());
        when(metadataResolver.resolveLastUpdated(link)).thenReturn(updatedAt);
        when(repository.save(chatId, link, List.of(), List.of(), updatedAt)).thenThrow(duplicateKeyException);

        LinkAlreadyTrackedException actual =
                assertThrows(LinkAlreadyTrackedException.class, () -> linkService.add(chatId, request));

        assertEquals("Link already tracked: " + link, actual.getMessage());
        assertSame(duplicateKeyException, actual.getCause());
        verify(accessValidator).ensureChatExists(chatId);
        verify(repository).save(chatId, link, List.of(), List.of(), updatedAt);
    }
}
