package backend.academy.linktracker.scrapper.service;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Допустимые префиксы пути для ссылок на вопросы StackOverflow.
 */
enum StackoverflowQuestionPathPrefix {
    /** Полный префикс пути вопроса: {@code /questions/{id}}. */
    QUESTIONS("questions"),
    /** Короткий префикс пути вопроса: {@code /q/{id}}. */
    SHORT("q");

    /**
     * Карта соответствия символьного представления и enum-значения.
     */
    private static final Map<String, StackoverflowQuestionPathPrefix> BY_SYMBOL = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(StackoverflowQuestionPathPrefix::symbol, Function.identity()));

    /**
     * Символьный префикс в URL-пути.
     */
    private final String symbol;

    /**
     * Создает enum-значение для указанного префикса.
     *
     * @param symbol символьный префикс пути
     */
    StackoverflowQuestionPathPrefix(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Возвращает символьный префикс для текущего enum-значения.
     *
     * @return символьный префикс в URL-пути
     */
    String symbol() {
        return symbol;
    }

    /**
     * Возвращает enum-значение по символьному префиксу.
     *
     * @param symbol символьный префикс из URL-пути
     * @return найденное enum-значение или пустой результат
     */
    static Optional<StackoverflowQuestionPathPrefix> fromSymbol(String symbol) {
        return Optional.ofNullable(BY_SYMBOL.get(symbol));
    }
}
