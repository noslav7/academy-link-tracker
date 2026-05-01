package backend.academy.linktracker.scrapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

/**
 * Общий test-helper для задания с отказоустойчивым Valkey.
 *
 * <p>Поднимает кластер из трех нод через Testcontainers, инициализирует его один раз
 * и предоставляет свойства подключения для разных тестовых контекстов Spring.
 */
public final class ValkeyClusterTestcontainers {

    private static final DockerImageName VALKEY_IMAGE = DockerImageName.parse("valkey/valkey:8.1-alpine");
    private static final int VALKEY_PORT = 6379;
    private static final int CLUSTER_READY_TIMEOUT_MS = 15_000;
    private static final int CLUSTER_READY_POLL_INTERVAL_MS = 200;

    private static final Network NETWORK = Network.newNetwork();
    private static final GenericContainer<?> NODE_1 = createNode("valkey-node-1");
    private static final GenericContainer<?> NODE_2 = createNode("valkey-node-2");
    private static final GenericContainer<?> NODE_3 = createNode("valkey-node-3");
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    /**
     * Утилитный класс: создание экземпляра не требуется.
     */
    private ValkeyClusterTestcontainers() {}

    /**
     * Стартует три ноды Valkey и инициализирует кластер.
     *
     * <p>Нужен для выполнения требований задания: тесты работают не с одиночным инстансом,
     * а с 3-нодовым кластером. Повторные вызовы безопасны.
     */
    public static void start() {
        if (STARTED.get()) {
            return;
        }
        synchronized (ValkeyClusterTestcontainers.class) {
            if (STARTED.get()) {
                return;
            }
            Startables.deepStart(Stream.of(NODE_1, NODE_2, NODE_3)).join();
            initializeCluster();
            STARTED.set(true);
        }
    }

    /**
     * Регистрирует свойства Spring для подключения в кластерном режиме.
     *
     * <p>Используется там, где тестовый контекст должен обращаться к Valkey как к кластеру.
     */
    public static void registerSpringClusterProperties(DynamicPropertyRegistry registry) {
        start();
        registry.add("spring.data.redis.cluster.nodes", () -> String.join(",", nodeAddresses()));
    }

    /**
     * Регистрирует свойства Spring для подключения к первой ноде как к standalone.
     *
     * <p>Используется для совместимости существующих тестов кэша, где достаточно доступа к данным
     * через master-ноду, но инфраструктура все равно поднимается кластерной.
     */
    public static void registerSpringStandaloneProperties(DynamicPropertyRegistry registry) {
        start();
        registry.add("spring.data.redis.host", NODE_1::getHost);
        registry.add("spring.data.redis.port", () -> NODE_1.getMappedPort(VALKEY_PORT));
    }

    /**
     * Возвращает список host:port всех трех нод.
     *
     * <p>Нужно для настройки клиентских подключений в кластерном режиме.
     */
    public static List<String> nodeAddresses() {
        start();
        return List.of(
                NODE_1.getHost() + ":" + NODE_1.getMappedPort(VALKEY_PORT),
                NODE_2.getHost() + ":" + NODE_2.getMappedPort(VALKEY_PORT),
                NODE_3.getHost() + ":" + NODE_3.getMappedPort(VALKEY_PORT));
    }

    /**
     * Возвращает host первой ноды (master после инициализации кластера).
     */
    public static String firstNodeHost() {
        start();
        return NODE_1.getHost();
    }

    /**
     * Возвращает порт первой ноды на хост-машине.
     */
    public static int firstNodePort() {
        start();
        return NODE_1.getMappedPort(VALKEY_PORT);
    }

    /**
     * Создает контейнер отдельной ноды с включенным cluster mode.
     *
     * <p>Настройки `cluster-enabled` и `nodes.conf` обязательны, чтобы нода могла участвовать в кластере.
     */
    @SuppressWarnings("resource")
    private static GenericContainer<?> createNode(String nodeAlias) {
        return new GenericContainer<>(VALKEY_IMAGE)
                .withNetwork(NETWORK)
                .withNetworkAliases(nodeAlias)
                .withExposedPorts(VALKEY_PORT)
                .withCommand(
                        "valkey-server",
                        "--port",
                        String.valueOf(VALKEY_PORT),
                        "--bind",
                        "0.0.0.0",
                        "--protected-mode",
                        "no",
                        "--appendonly",
                        "yes",
                        "--cluster-enabled",
                        "yes",
                        "--cluster-config-file",
                        "nodes.conf",
                        "--cluster-node-timeout",
                        "5000");
    }

    /**
     * Выполняет `valkey-cli --cluster create` после старта контейнеров.
     *
     * <p>Используется схема 1 master + 2 replica (`--cluster-replicas 2`) для базовой отказоустойчивости,
     * которая требуется по условию задания.
     */
    private static void initializeCluster() {
        try {
            var result = NODE_1.execInContainer(
                    "sh",
                    "-c",
                    "valkey-cli --cluster create "
                            + "valkey-node-1:"
                            + VALKEY_PORT
                            + " "
                            + "valkey-node-2:"
                            + VALKEY_PORT
                            + " "
                            + "valkey-node-3:"
                            + VALKEY_PORT
                            + " "
                            + "--cluster-replicas 2 --cluster-yes");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Unable to initialize Valkey cluster: " + result.getStderr());
            }
            waitUntilClusterReady();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize Valkey cluster", exception);
        }
    }

    /**
     * Ждет, пока кластер полностью поднимется и перейдет в `cluster_state:ok`.
     *
     * <p>Без этого первые запросы из тестов могут получить `CLUSTERDOWN`, даже если
     * команда `cluster create` уже завершилась успешно.
     */
    private static void waitUntilClusterReady() throws Exception {
        long deadline = System.currentTimeMillis() + CLUSTER_READY_TIMEOUT_MS;
        String lastClusterInfo = "";
        while (System.currentTimeMillis() < deadline) {
            var result = NODE_1.execInContainer(
                    "sh", "-c", "valkey-cli -h valkey-node-1 -p " + VALKEY_PORT + " cluster info");
            if (result.getExitCode() == 0 && result.getStdout().contains("cluster_state:ok")) {
                return;
            }
            lastClusterInfo = result.getStdout() + result.getStderr();
            Thread.sleep(CLUSTER_READY_POLL_INTERVAL_MS);
        }
        throw new IllegalStateException("Valkey cluster did not become ready. Last cluster info: " + lastClusterInfo);
    }
}
