package backend.academy.linktracker.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа в приложение Telegram-бота.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BotApplication {

    /**
     * Запускает Spring Boot контекст приложения бота.
     *
     * @param args аргументы командной строки
     */
    static void main(String[] args) {
        SpringApplication.run(BotApplication.class, args);
    }
}
