package eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A previous run's report, read as the known-good reference (CONTEXT.md: Baseline). Only which
 * cases passed is used, so any report the harness wrote can be promoted by copying it to
 * caseResults/baseline.json.
 */
final class Baseline {
  private final String name;
  private final Map<String, Boolean> passedInBaselineByCaseId;

  private Baseline(String name, Map<String, Boolean> passedInBaselineByCaseId) {
    this.name = name;
    this.passedInBaselineByCaseId = passedInBaselineByCaseId;
  }

  /**
   * The baseline named by the {@code baseline} setting. No path set: logged, and the run goes ahead
   * without a baseline (null). A path with no usable file behind it (missing, not JSON, ...)
   * throws, and the run exits 2, so a mistyped path never silently drops the regression check.
   */
  static Baseline resolve(EvalConfig config, PrintStream out) throws IOException {
    Path file = config.baselineFile();
    if (file == null) {
      out.println("No baseline set, running without one (no regression check).");
      return null;
    }
    return load(file);
  }

  static Baseline load(Path file) throws IOException {
    String name = file.getFileName().toString();
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("baseline file not found: " + file);
    }
    JsonNode cases;
    try {
      cases = new ObjectMapper().readTree(file.toFile()).path("cases");
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "baseline " + name + " is not valid JSON: " + e.getOriginalMessage());
    }
    if (!cases.isArray()) {
      throw new IllegalArgumentException(
          "baseline " + name + " has no 'cases' list; pass a report the harness wrote");
    }
    if (cases.isEmpty()) {
      throw new IllegalArgumentException(
          "baseline " + name + " has an empty 'cases' list, so it could never flag a regression");
    }
    Map<String, Boolean> passedInBaselineByCaseId = new HashMap<>();
    for (int index = 0; index < cases.size(); index++) {
      JsonNode entry = cases.get(index);
      if (!entry.path("id").isTextual() || !entry.path("passed").isBoolean()) {
        throw new IllegalArgumentException(
            "baseline "
                + name
                + ": case #"
                + (index + 1)
                + " needs a text 'id' and a boolean 'passed'");
      }
      String caseId = entry.get("id").asText();
      if (passedInBaselineByCaseId.put(caseId, entry.get("passed").asBoolean()) != null) {
        throw new IllegalArgumentException(
            "baseline " + name + " has a duplicate case id '" + caseId + "'");
      }
    }
    return new Baseline(name, passedInBaselineByCaseId);
  }

  /** A baseline that shares no case with the run could never flag a regression: an error. */
  void requireAnyOf(List<EvalCase> cases) {
    if (cases.stream().noneMatch(evalCase -> knows(evalCase.id()))) {
      throw new IllegalArgumentException(
          "baseline "
              + name
              + " has none of this run's cases, so it could never flag a regression");
    }
  }

  /** True only when the case is in the baseline and passed there. */
  boolean passed(String caseId) {
    return passedInBaselineByCaseId.getOrDefault(caseId, false);
  }

  /** True only when the case is in the baseline and failed there. */
  boolean failed(String caseId) {
    return Boolean.FALSE.equals(passedInBaselineByCaseId.get(caseId));
  }

  /** True when the case is in the baseline, whether it passed there or not. */
  boolean knows(String caseId) {
    return passedInBaselineByCaseId.containsKey(caseId);
  }

  String name() {
    return name;
  }
}
