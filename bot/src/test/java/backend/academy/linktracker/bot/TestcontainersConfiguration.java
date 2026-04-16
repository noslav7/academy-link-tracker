package backend.academy.linktracker.bot;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

    static {
        KAFKA.start();
        System.setProperty("app.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
    }

    // Uncomment to start PostgreSQLContainer
    // @Bean
    // @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

    // Uncomment to start RedisContainer
    // @Bean
    // @ServiceConnection
    RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis:8.2-alpine"));
    }

    @Bean
    KafkaContainer kafkaContainer() {
        return KAFKA;
    }
}
