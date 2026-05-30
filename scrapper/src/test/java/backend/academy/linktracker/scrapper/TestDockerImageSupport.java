package backend.academy.linktracker.scrapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class TestDockerImageSupport {
    private static final Set<String> BUILT_IMAGES = ConcurrentHashMap.newKeySet();
    private static final Path PROJECT_ROOT = resolveProjectRoot();

    private TestDockerImageSupport() {}

    static void ensureImageBuilt(String imageTag, String dockerfileRelativePath) {
        if (BUILT_IMAGES.add(imageTag)) {
            buildDockerImage(imageTag, PROJECT_ROOT.resolve(dockerfileRelativePath));
        }
    }

    private static Path resolveProjectRoot() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null && !multiModuleRoot.isBlank()) {
            Path fromProperty =
                    findProjectRoot(Path.of(multiModuleRoot).toAbsolutePath().normalize());
            if (fromProperty != null) {
                return fromProperty;
            }
        }

        Path fromCwd = findProjectRoot(Path.of("").toAbsolutePath().normalize());
        if (fromCwd != null) {
            return fromCwd;
        }

        throw new IllegalStateException(
                "Unable to resolve multi-module project root directory for Testcontainers build context");
    }

    private static Path findProjectRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (hasRequiredModules(current)) {
                return current;
            }
            Path nested = current.resolve("link-tracker");
            if (hasRequiredModules(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean hasRequiredModules(Path candidate) {
        return Files.exists(candidate.resolve("bot/pom.xml")) && Files.exists(candidate.resolve("scrapper/pom.xml"));
    }

    private static void buildDockerImage(String imageTag, Path dockerfile) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "build", "-f", dockerfile.toString(), "-t", imageTag, PROJECT_ROOT.toString());
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("docker build failed for image '%s'%n%s".formatted(imageTag, output));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while building Docker image: " + imageTag, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to execute docker build for image: " + imageTag, ex);
        }
    }
}
