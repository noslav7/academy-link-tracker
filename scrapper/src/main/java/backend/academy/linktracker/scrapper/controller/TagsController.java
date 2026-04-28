package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.dto.AddTagRequest;
import backend.academy.linktracker.scrapper.dto.ListTagsResponse;
import backend.academy.linktracker.scrapper.dto.RemoveTagRequest;
import backend.academy.linktracker.scrapper.dto.UpdateTagRequest;
import backend.academy.linktracker.scrapper.service.TagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class TagsController {

    /**
     * Сервис управления тегами подписок.
     */
    private final TagService tagService;

    /**
     * Создает контроллер CRUD-операций для тегов.
     *
     * @param tagService сервис управления тегами
     */
    public TagsController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * Возвращает список тегов для указанной ссылки в чате.
     *
     * @param chatId идентификатор Telegram-чата
     * @param link URL отслеживаемой ссылки
     * @return список тегов подписки
     */
    @GetMapping
    public ResponseEntity<ListTagsResponse> list(
            @RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId,
            @RequestParam("link") @NotBlank @Size(max = 2048) String link) {
        return ResponseEntity.ok(tagService.list(chatId, link));
    }

    /**
     * Добавляет тег к подписке.
     *
     * @param chatId идентификатор Telegram-чата
     * @param request тело запроса добавления тега
     * @return пустой успешный ответ
     */
    @PostMapping
    public ResponseEntity<Void> add(
            @RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId, @Valid @RequestBody AddTagRequest request) {
        tagService.add(chatId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Переименовывает тег у подписки.
     *
     * @param chatId идентификатор Telegram-чата
     * @param request тело запроса обновления тега
     * @return пустой успешный ответ
     */
    @PutMapping
    public ResponseEntity<Void> update(
            @RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId, @Valid @RequestBody UpdateTagRequest request) {
        tagService.update(chatId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Удаляет тег у подписки.
     *
     * @param chatId идентификатор Telegram-чата
     * @param request тело запроса удаления тега
     * @return пустой успешный ответ
     */
    @DeleteMapping
    public ResponseEntity<Void> remove(
            @RequestHeader(ApiHeaders.TG_CHAT_ID) long chatId, @Valid @RequestBody RemoveTagRequest request) {
        tagService.delete(chatId, request);
        return ResponseEntity.ok().build();
    }
}
