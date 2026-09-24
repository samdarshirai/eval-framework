package eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Run-id generation and the JSON report file. */
final class ReportWriter {
  private ReportWriter() {}

  /** A UTC timestamp id, yyyyMMdd-HHmmss. */
  static String newRunId() {
    return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now());
  }

  /** Writes &lt;runId&gt;.json into outputDir, creating it; returns the file written. */
  static Path write(Path outputDir, SuiteReport report) throws IOException {
    Files.createDirectories(outputDir);
    Path file = outputDir.resolve(report.runId() + ".json");
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
    return file;
  }
}
