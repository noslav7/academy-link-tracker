package backend.academy.linktracker.bot.properties;

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
}
