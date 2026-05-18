package backend.academy.linktracker.ai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Приоритет обработанного обновления для маршрутизации и сортировки уведомлений.
 */
public enum UpdatePriority {
    /**
     * Высокий приоритет.
     */
    HIGH("HIGH"),

    /**
     * Средний приоритет.
     */
    MEDIUM("MEDIUM"),

    /**
     * Низкий приоритет.
     */
    LOW("LOW");

    /**
     * Единый словарь сопоставления "символ -> enum-значение".
     */
    private static final Map<String, UpdatePriority> BY_SYMBOL =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(UpdatePriority::symbol, value -> value));

    /**
     * Канонический строковый символ приоритета для сериализации/десериализации.
     */
    private final String symbol;

    /**
     * Создает enum-значение с фиксированным строковым символом.
     */
    UpdatePriority(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Возвращает стабильный строковый символ приоритета.
     */
    @JsonValue
    public String symbol() {
        return symbol;
    }

    /**
     * Преобразует строковый символ в enum и отклоняет невалидные значения.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static UpdatePriority fromSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("UpdatePriority symbol must not be blank");
        }
        UpdatePriority value = BY_SYMBOL.get(symbol.trim().toUpperCase(Locale.ROOT));
        if (value == null) {
            throw new IllegalArgumentException("Unsupported UpdatePriority symbol: " + symbol);
        }
        return value;
    }

    /**
     * Возвращает максимальный приоритет из двух значений.
     */
    public static UpdatePriority max(UpdatePriority left, UpdatePriority right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return left.ordinal() <= right.ordinal() ? left : right;
    }
}
