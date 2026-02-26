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

@RestController
@RequestMapping("/links")
public class LinksController {

    private static final String CHAT_HEADER = "Tg-Chat-Id";

    private final LinkService linkService;

    public LinksController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping
    public ResponseEntity<ListLinksResponse> list(@RequestHeader(CHAT_HEADER) long chatId) {
        List<LinkResponse> links = linkService.list(chatId);
        return ResponseEntity.ok(new ListLinksResponse(links, links.size()));
    }

    @PostMapping
    public ResponseEntity<LinkResponse> add(
            @RequestHeader(CHAT_HEADER) long chatId, @RequestBody AddLinkRequest request) {
        return ResponseEntity.ok(linkService.add(chatId, request));
    }

    @DeleteMapping
    public ResponseEntity<LinkResponse> remove(
            @RequestHeader(CHAT_HEADER) long chatId, @RequestBody RemoveLinkRequest request) {
        return ResponseEntity.ok(linkService.remove(chatId, request.link()));
    }
}
