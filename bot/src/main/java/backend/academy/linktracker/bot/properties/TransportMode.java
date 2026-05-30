package backend.academy.linktracker.bot.properties;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Режим транспортного взаимодействия со scrapper.
 */
public enum TransportMode {
    /**
     * HTTP REST транспорт.
     */
    HTTP,
    /**
     * gRPC транспорт.
     */
    GRPC;

    /**
     * Символьное значение HTTP режима для property-конфигурации.
     */
    public static final String HTTP_VALUE = "HTTP";
    /**
     * Символьное значение gRPC режима для property-конфигурации.
     */
    public static final String GRPC_VALUE = "GRPC";

    /**
     * Единый словарь соответствия "символ из конфигурации -> enum".
     * Нужен, чтобы централизовать валидацию и преобразование входного значения транспорта.
     */
    private static final Map<String, TransportMode> BY_SYMBOL =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(TransportMode::symbol, mode -> mode));

    /**
     * Преобразует символическое значение в enum с валидацией.
     *
     * @param symbol символическое имя режима
     * @return транспортный режим
     * @throws IllegalArgumentException если передано неподдерживаемое значение
     * @implNote Нужен fail-fast при ошибке конфигурации транспорта, чтобы приложение не запускалось в невалидном
     *     состоянии.
     */
    public static TransportMode fromSymbol(String symbol) {
        TransportMode mode = BY_SYMBOL.get(symbol);
        if (mode != null) {
            return mode;
        }
        throw new IllegalArgumentException("Unsupported transport mode: " + symbol);
    }

    /**
     * Возвращает канонический символ режима транспорта.
     * Нужен как единая точка построения ключа для словаря {@code BY_SYMBOL}.
     *
     * @return строковый символ enum-значения
     */
    private String symbol() {
        return name();
    }
}
