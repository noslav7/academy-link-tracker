package backend.academy.linktracker.scrapper.properties;

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
     */
    public static final String HTTP_VALUE = "HTTP";
    /**
     * Символьное значение gRPC режима для property-конфигурации.
     */
    public static final String GRPC_VALUE = "GRPC";

    /**
     * Символьное значение Kafka режима для property-конфигурации.
     */
    public static final String KAFKA_VALUE = "KAFKA";
}
