package backend.academy.linktracker.scrapper.properties;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Режим транспортного взаимодействия с bot-сервисом.
 */
public enum TransportMode {
    /**
     * HTTP REST транспорт.
     */
    HTTP,
    /**
     * gRPC транспорт.
     */
    GRPC,
    /**
     * Kafka транспорт (асинхронный через очередь сообщений).
     */
    KAFKA;

    /**
     * Символьное значение HTTP режима для property-конфигурации.
     * Нужен как константа времени компиляции для аннотаций конфигурации.
     */
    public static final String HTTP_VALUE = "HTTP";
    /**
     * Символьное значение gRPC режима для property-конфигурации.
     * Нужен как константа времени компиляции для аннотаций конфигурации.
     */
    public static final String GRPC_VALUE = "GRPC";

    /**
     * Символьное значение Kafka режима для property-конфигурации.
     * Нужен как константа времени компиляции для аннотаций конфигурации.
     */
    public static final String KAFKA_VALUE = "KAFKA";

    /**
     * Единый словарь соответствия "символ из конфигурации -> enum".
     * Используется, чтобы централизованно валидировать входные значения транспорта.
     */
    private static final Map<String, TransportMode> BY_SYMBOL =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(TransportMode::symbol, mode -> mode));

    /**
     * Преобразует символическое значение в enum с валидацией.
     *
     * @param symbol символическое имя режима
     * @return транспортный режим
     * @throws IllegalArgumentException если передано неподдерживаемое значение
     */
    public static TransportMode fromSymbol(String symbol) {
        TransportMode mode = BY_SYMBOL.get(symbol);
        if (mode != null) {
            return mode;
        }
        throw new IllegalArgumentException("Unsupported transport mode: " + symbol);
    }

    /**
     * Возвращает символическое представление режима.
     * Нужен как единая точка формирования ключей для словаря символов.
     *
     * @return строковый символ режима
     */
    private String symbol() {
        return name();
    }
}
