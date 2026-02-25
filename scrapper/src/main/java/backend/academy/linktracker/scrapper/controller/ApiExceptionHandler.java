package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.dto.ApiErrorResponse;
import backend.academy.linktracker.scrapper.exception.ChatAlreadyRegisteredException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.exception.UnsupportedLinkException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
        ChatNotFoundException.class,
        LinkNotFoundException.class,
    })
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler({
        LinkAlreadyTrackedException.class,
        ChatAlreadyRegisteredException.class,
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler({UnsupportedLinkException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, RuntimeException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
                ex.getMessage(),
                String.valueOf(status.value()),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                Arrays.stream(ex.getStackTrace()).limit(20).map(StackTraceElement::toString).toList());
        return ResponseEntity.status(status).body(response);
    }
}
