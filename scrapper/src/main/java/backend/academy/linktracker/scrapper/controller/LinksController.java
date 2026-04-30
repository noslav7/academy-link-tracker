package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.dto.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.service.CachedLinksService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер управления отслеживаемыми ссылками чата.
 */
@RestController
public class LinksController {

    /**
     * Сервис управления ссылками.
     */
    private final CachedLinksService cachedLinksService;

    /**
     * Создает контроллер управления ссылками.
     *
     * @param cachedLinksService прослойка кэширования ответов API ссылок
     */
    public LinksController(CachedLinksService cachedLinksService) {
        this.cachedLinksService = cachedLinksService;
    }

    /**
     * Возвращает список всех ссылок, отслеживаемых указанным чатом.
     *
     * @param chatId идентификатор Telegram-чата из заголовка
     * @return список ссылок и их количество
     */
    @GetMapping("/list")
    public ListLinksResponse list(@RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId) {
        return cachedLinksService.list(chatId);
    }

    /**
     * Добавляет новую ссылку в отслеживание.
     *
     * @param chatId идентификатор Telegram-чата из заголовка
     * @param request тело запроса с ссылкой, тегами и фильтрами
     * @return добавленная ссылка
     */
    @PostMapping("/links")
    public ResponseEntity<LinkResponse> add(
            @RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId, @Valid @RequestBody AddLinkRequest request) {
        return ResponseEntity.ok(cachedLinksService.add(chatId, request));
    }

    /**
     * Удаляет ссылку из отслеживания.
     *
     * @param chatId идентификатор Telegram-чата из заголовка
     * @param request тело запроса с удаляемой ссылкой
     * @return удаленная ссылка
     */
    @DeleteMapping("/links")
    public ResponseEntity<LinkResponse> remove(
            @RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId, @Valid @RequestBody RemoveLinkRequest request) {
        return ResponseEntity.ok(cachedLinksService.remove(chatId, request.link()));
    }
}
