package eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Run-id generation and the JSON report file under {@code caseResults/}. */
final class ReportWriter {
  private ReportWriter() {}

  /** A UTC timestamp id, yyyyMMdd-HHmmss. */
  static String newRunId() {
    return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now());
  }

  /** Writes caseResults/&lt;runId&gt;.json under root, creating the directory. */
  static void write(Path root, SuiteReport report) throws IOException {
    Files.createDirectories(root.resolve("caseResults"));
    new ObjectMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValue(root.resolve("caseResults/" + report.runId() + ".json").toFile(), report);
  }
}
