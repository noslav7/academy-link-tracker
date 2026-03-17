package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.LinkResponse;
import backend.academy.linktracker.scrapper.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.dto.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.service.LinkService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер управления отслеживаемыми ссылками чата.
 */
@RestController
@RequestMapping("/links")
public class LinksController {

    /**
     * Сервис управления ссылками.
     */
    private final LinkService linkService;

    /**
     * Создает контроллер управления ссылками.
     *
     * @param linkService сервис управления ссылками
     */
    public LinksController(LinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * Возвращает список всех ссылок, отслеживаемых указанным чатом.
     *
     * @param chatId идентификатор Telegram-чата из заголовка
     * @return список ссылок и их количество
     */
    @GetMapping
    public ResponseEntity<ListLinksResponse> list(@RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId) {
        List<LinkResponse> links = linkService.list(chatId);
        return ResponseEntity.ok(new ListLinksResponse(links, links.size()));
    }

    /**
     * Добавляет новую ссылку в отслеживание.
     *
     * @param chatId идентификатор Telegram-чата из заголовка
     * @param request тело запроса с ссылкой, тегами и фильтрами
     * @return добавленная ссылка
     */
    @PostMapping
    public ResponseEntity<LinkResponse> add(
            @RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId, @RequestBody AddLinkRequest request) {
        return ResponseEntity.ok(linkService.add(chatId, request));
    }

    /**
     * Удаляет ссылку из отслеживания.
     *
     * @param chatId идентификатор Telegram-чата из заголовка
     * @param request тело запроса с удаляемой ссылкой
     * @return удаленная ссылка
     */
    @DeleteMapping
    public ResponseEntity<LinkResponse> remove(
            @RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId, @RequestBody RemoveLinkRequest request) {
        return ResponseEntity.ok(linkService.remove(chatId, request.link()));
    }
}
