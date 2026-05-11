package backend.academy.linktracker.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа AI Agent, нужна для запуска Spring Boot контекста и загрузки настроек модуля.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiAgentApplication {

    /**
     * Запускает приложение, чтобы сервис обработки обновлений был доступен как отдельный модуль.
     *
     * @param args аргументы командной строки
     */
    static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}
