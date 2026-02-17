package backend.academy.linktracker.scrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
/**
 * Точка входа в приложение scrapper-сервиса.
 */
public class ScrapperApplication {

    /**
     * Запускает Spring Boot контекст scrapper-сервиса.
     *
     * @param args аргументы командной строки
     */
    static void main(String[] args) {
        SpringApplication.run(ScrapperApplication.class, args);
    }
}
