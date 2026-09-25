package eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A previous run's report, read as the known-good reference (CONTEXT.md: Baseline). Only which
 * cases passed is used, so any report the harness wrote can be promoted by copying it to
 * caseResults/baseline.json.
 */
final class Baseline {
  private final String name;
  private final Map<String, Boolean> passedById;

  private Baseline(String name, Map<String, Boolean> passedById) {
    this.name = name;
    this.passedById = passedById;
  }

  static Baseline load(Path file) throws IOException {
    String name = file.getFileName().toString();
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("baseline file not found: " + name);
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
    Map<String, Boolean> passedById = new HashMap<>();
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
      passedById.put(entry.get("id").asText(), entry.get("passed").asBoolean());
    }
    return new Baseline(name, passedById);
  }

  /** True only when the case is in the baseline and passed there. */
  boolean passed(String caseId) {
    return passedById.getOrDefault(caseId, false);
  }

  /** True when the case is in the baseline, whether it passed there or not. */
  boolean knows(String caseId) {
    return passedById.containsKey(caseId);
  }

  String name() {
    return name;
  }
}
