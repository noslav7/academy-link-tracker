package backend.academy.linktracker.scrapper.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JmeterMarkdownReportGenerator {

    private static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);
    private static final List<String> SUMMARY_ENDPOINTS = List.of("GET /list", "POST/DELETE /links");

    private JmeterMarkdownReportGenerator() {}

    public static void main(String[] args) throws IOException {
        String inputProperty = System.getProperty("jmeter.report.input");
        if (inputProperty == null || inputProperty.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing -Djmeter.report.input. Example: -Djmeter.report.input=scrapper/target/jmeter/results.jtl");
        }

        Path inputPath = Path.of(inputProperty);
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("JMeter results file not found: " + inputPath.toAbsolutePath());
        }

        Path outputPath = Path.of(System.getProperty("jmeter.report.output", "scrapper/LOAD_TEST_REPORT.md"));
        String scenarioName = System.getProperty("jmeter.report.scenario", "JMeter run");

        ScenarioMetrics scenarioMetrics = parseJtl(inputPath);
        String report = buildReport(scenarioName, inputPath, scenarioMetrics);

        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        Files.writeString(outputPath, report, StandardCharsets.UTF_8);
    }

    private static ScenarioMetrics parseJtl(Path inputPath) throws IOException {
        List<String> lines = Files.readAllLines(inputPath, StandardCharsets.UTF_8);
        if (lines.size() < 2) {
            throw new IllegalArgumentException("JTL file is empty or contains only header: " + inputPath);
        }

        List<String> header = parseCsvLine(lines.get(0));
        int labelIndex = columnIndex(header, "label");
        int elapsedIndex = columnIndex(header, "elapsed");
        int responseCodeIndex = columnIndex(header, "responseCode");
        int responseMessageIndex = columnIndex(header, "responseMessage");
        int timestampIndex = columnIndex(header, "timeStamp");

        Map<String, EndpointMetrics> endpointMetricsMap = new LinkedHashMap<>();
        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = Long.MIN_VALUE;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(line);
            if (values.size()
                    <= Math.max(Math.max(labelIndex, elapsedIndex), Math.max(responseCodeIndex, timestampIndex))) {
                continue;
            }

            String label = values.get(labelIndex).trim();
            if (label.isBlank()) {
                continue;
            }

            long elapsedMs = safeParseLong(values.get(elapsedIndex), 0L);
            String responseCode = values.get(responseCodeIndex).trim();
            String responseMessage = responseMessageIndex >= 0 && responseMessageIndex < values.size()
                    ? values.get(responseMessageIndex).trim()
                    : "";
            long timestamp = safeParseLong(values.get(timestampIndex), -1L);

            if (timestamp > 0) {
                minTimestamp = Math.min(minTimestamp, timestamp);
                maxTimestamp = Math.max(maxTimestamp, timestamp);
            }

            String normalizedEndpoint = normalizeEndpoint(label);
            EndpointMetrics endpointMetrics =
                    endpointMetricsMap.computeIfAbsent(normalizedEndpoint, ignored -> new EndpointMetrics());
            endpointMetrics.record(elapsedMs, responseCode, responseMessage);
        }

        long durationMs = (minTimestamp == Long.MAX_VALUE || maxTimestamp == Long.MIN_VALUE)
                ? 1_000L
                : Math.max(1_000L, maxTimestamp - minTimestamp);
        return new ScenarioMetrics(endpointMetricsMap, durationMs);
    }

    private static String normalizeEndpoint(String label) {
        if ("GET /list".equals(label)) {
            return "GET /list";
        }
        if ("POST /links".equals(label) || "DELETE /links".equals(label)) {
            return "POST/DELETE /links";
        }
        return label;
    }

    private static String buildReport(String scenarioName, Path inputPath, ScenarioMetrics scenarioMetrics) {
        StringBuilder report = new StringBuilder();
        report.append("# /list cache load test report (JMeter)")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
        report.append("- Run date: ")
                .append(UTC_FORMATTER.format(Instant.now()))
                .append(System.lineSeparator());
        report.append("- Source JTL: `")
                .append(inputPath.toString().replace('\\', '/'))
                .append("`")
                .append(System.lineSeparator());
        report.append("- Duration window, sec: ")
                .append(new DecimalFormat("0.00").format(scenarioMetrics.durationMs() / 1_000.0))
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        report.append("## ").append(scenarioName).append(System.lineSeparator()).append(System.lineSeparator());

        for (String endpoint : SUMMARY_ENDPOINTS) {
            EndpointMetrics metrics = scenarioMetrics.byEndpoint().get(endpoint);
            appendEndpoint(report, endpoint, metrics, scenarioMetrics.durationMs());
        }

        return report.toString();
    }

    private static void appendEndpoint(
            StringBuilder report, String endpoint, EndpointMetrics metrics, long durationMs) {
        report.append("### ").append(endpoint).append(System.lineSeparator()).append(System.lineSeparator());
        if (metrics == null || metrics.total() == 0) {
            report.append("- Request: `").append(endpoint).append("`").append(System.lineSeparator());
            report.append("- Total calls: 0").append(System.lineSeparator());
            report.append("- Most frequent errors: none")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
            return;
        }

        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        report.append("- Request: `").append(endpoint).append("`").append(System.lineSeparator());
        report.append("- RPS: ")
                .append(decimalFormat.format(metrics.total() / Math.max(1.0, durationMs / 1_000.0)))
                .append(System.lineSeparator());
        report.append("- Average latency, ms: ")
                .append(decimalFormat.format(metrics.averageLatencyMs()))
                .append(System.lineSeparator());
        report.append("- P50, ms: ")
                .append(decimalFormat.format(metrics.percentile(0.50)))
                .append(System.lineSeparator());
        report.append("- P99, ms: ")
                .append(decimalFormat.format(metrics.percentile(0.99)))
                .append(System.lineSeparator());
        report.append("- HTTP 200 count: ").append(metrics.statusCount("200")).append(System.lineSeparator());
        report.append("- HTTP 502/504 count: ")
                .append(metrics.statusCount("502") + metrics.statusCount("504"))
                .append(System.lineSeparator());
        report.append("- HTTP 500 count: ").append(metrics.statusCount("500")).append(System.lineSeparator());
        report.append("- Total calls: ").append(metrics.total()).append(System.lineSeparator());
        report.append("- Most frequent errors: ")
                .append(formatTopErrors(metrics.topErrors()))
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private static String formatTopErrors(Map<String, Long> errors) {
        if (errors.isEmpty()) {
            return "none";
        }
        return errors.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .reduce((left, right) -> left + "; " + right)
                .orElse("none");
    }

    private static int columnIndex(List<String> header, String columnName) {
        for (int i = 0; i < header.size(); i++) {
            if (columnName.equals(header.get(i))) {
                return i;
            }
        }
        if ("responseMessage".equals(columnName)) {
            return -1;
        }
        throw new IllegalArgumentException("JTL header does not contain column: " + columnName);
    }

    private static long safeParseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static final class ScenarioMetrics {
        private final Map<String, EndpointMetrics> byEndpoint;
        private final long durationMs;

        private ScenarioMetrics(Map<String, EndpointMetrics> byEndpoint, long durationMs) {
            this.byEndpoint = byEndpoint;
            this.durationMs = durationMs;
        }

        private Map<String, EndpointMetrics> byEndpoint() {
            return byEndpoint;
        }

        private long durationMs() {
            return durationMs;
        }
    }

    private static final class EndpointMetrics {
        private final List<Long> latencies = new ArrayList<>();
        private final Map<String, Long> statusCounters = new HashMap<>();
        private final Map<String, Long> errors = new HashMap<>();
        private long total;
        private long sumLatencyMs;

        private void record(long elapsedMs, String statusCode, String responseMessage) {
            total++;
            latencies.add(elapsedMs);
            sumLatencyMs += elapsedMs;

            String normalizedStatus = statusCode == null || statusCode.isBlank() ? "-1" : statusCode.trim();
            statusCounters.put(normalizedStatus, statusCounters.getOrDefault(normalizedStatus, 0L) + 1L);

            if (!"200".equals(normalizedStatus)) {
                String message =
                        (responseMessage == null || responseMessage.isBlank()) ? "<no message>" : responseMessage;
                String errorKey = normalizedStatus + " / " + message;
                errors.put(errorKey, errors.getOrDefault(errorKey, 0L) + 1L);
            }
        }

        private long total() {
            return total;
        }

        private double averageLatencyMs() {
            return total == 0 ? 0.0 : (double) sumLatencyMs / total;
        }

        private double percentile(double percentile) {
            if (latencies.isEmpty()) {
                return 0.0;
            }
            List<Long> sorted = new ArrayList<>(latencies);
            sorted.sort(Long::compareTo);
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            if (index < 0) {
                index = 0;
            }
            if (index >= sorted.size()) {
                index = sorted.size() - 1;
            }
            return sorted.get(index);
        }

        private long statusCount(String statusCode) {
            return statusCounters.getOrDefault(statusCode, 0L);
        }

        private Map<String, Long> topErrors() {
            return errors;
        }
    }
}
