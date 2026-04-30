package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.dto.ApiErrorResponse;
import backend.academy.linktracker.scrapper.exception.ChatAlreadyRegisteredException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.exception.TagAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.TagNotFoundException;
import backend.academy.linktracker.scrapper.exception.UnsupportedLinkException;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Глобальный обработчик исключений REST API scrapper-сервиса.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Преобразует исключения отсутствующих ресурсов в HTTP 404.
     *
     * @param ex исходное исключение
     * @return стандартизированный ответ об ошибке
     */
    @ExceptionHandler({
        ChatNotFoundException.class,
        LinkNotFoundException.class,
        TagNotFoundException.class,
    })
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    /**
     * Преобразует конфликтные состояния в HTTP 409.
     *
     * @param ex исходное исключение
     * @return стандартизированный ответ об ошибке
     */
    @ExceptionHandler({
        LinkAlreadyTrackedException.class,
        ChatAlreadyRegisteredException.class,
        TagAlreadyExistsException.class,
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    /**
     * Преобразует ошибки валидации и неподдерживаемых ссылок в HTTP 400.
     *
     * @param ex исходное исключение
     * @return стандартизированный ответ об ошибке
     */
    @ExceptionHandler({UnsupportedLinkException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    /**
     * Преобразует ошибки bean validation в HTTP 400 с деталями полей.
     *
     * @param ex исходное исключение
     * @return стандартизированный ответ об ошибке валидации
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toFieldValidationMessage)
                .distinct()
                .reduce((left, right) -> left + "; " + right)
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, new IllegalArgumentException(message, ex));
    }

    /**
     * Преобразует ошибки валидации параметров метода в HTTP 400.
     *
     * @param ex исходное исключение
     * @return стандартизированный ответ об ошибке
     */
    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidationConstraintViolation(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    /**
     * Собирает унифицированное тело ошибки API.
     *
     * @param status HTTP-статус ответа
     * @param ex исходное исключение
     * @return объект ответа с деталями ошибки
     */
    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, Exception ex) {
        ApiErrorResponse response = new ApiErrorResponse(
                ex.getMessage(),
                String.valueOf(status.value()),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                Arrays.stream(ex.getStackTrace())
                        .limit(20)
                        .map(StackTraceElement::toString)
                        .toList());
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Формирует короткое текстовое описание ошибки валидации поля.
     *
     * @param fieldError ошибка валидации конкретного поля
     * @return строка формата {@code field: message}
     */
    private static String toFieldValidationMessage(FieldError fieldError) {
        String defaultMessage =
                fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage();
        return fieldError.getField() + ": " + defaultMessage;
    }
}
