package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.dto.AddTagRequest;
import backend.academy.linktracker.scrapper.dto.ListTagsResponse;
import backend.academy.linktracker.scrapper.dto.RemoveTagRequest;
import backend.academy.linktracker.scrapper.dto.UpdateTagRequest;
import backend.academy.linktracker.scrapper.service.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер CRUD-операций управления тегами подписок.
 */
@RestController
@RequestMapping("/tags")
public class TagsController {

    private static final String CHAT_HEADER = "Tg-Chat-Id";

    private final TagService tagService;

    public TagsController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ResponseEntity<ListTagsResponse> list(
            @RequestHeader(CHAT_HEADER) long chatId, @RequestParam("link") String link) {
        return ResponseEntity.ok(tagService.list(chatId, link));
    }

    @PostMapping
    public ResponseEntity<Void> add(@RequestHeader(CHAT_HEADER) long chatId, @RequestBody AddTagRequest request) {
        tagService.add(chatId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<Void> update(@RequestHeader(CHAT_HEADER) long chatId, @RequestBody UpdateTagRequest request) {
        tagService.update(chatId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@RequestHeader(CHAT_HEADER) long chatId, @RequestBody RemoveTagRequest request) {
        tagService.delete(chatId, request);
        return ResponseEntity.ok().build();
    }
}
