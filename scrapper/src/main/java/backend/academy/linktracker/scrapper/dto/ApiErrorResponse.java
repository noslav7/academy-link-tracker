package backend.academy.linktracker.scrapper.dto;

import java.util.List;

/**
 * DTO унифицированного ответа об ошибке API.
 *
 * @param description краткое описание ошибки
 * @param code HTTP-код ошибки
 * @param exceptionName имя исключения
 * @param exceptionMessage текст сообщения исключения
 * @param stacktrace фрагмент stack trace
 */
public record ApiErrorResponse(
        String description, String code, String exceptionName, String exceptionMessage, List<String> stacktrace) {}
