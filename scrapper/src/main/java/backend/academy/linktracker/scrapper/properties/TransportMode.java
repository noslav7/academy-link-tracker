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
    GRPC
}
