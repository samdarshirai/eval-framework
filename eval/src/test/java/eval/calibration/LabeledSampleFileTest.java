package eval.calibration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class LabeledSampleFileTest {
  private static List<Map<String, Object>> sample() throws IOException {
    try (Reader reader =
        new InputStreamReader(
            LabeledSampleFileTest.class.getResourceAsStream("/calibration/labeled-sample.yaml"),
            StandardCharsets.UTF_8)) {
      return new Yaml().load(reader);
    }
  }

  private static String collapse(String text) {
    return text.replaceAll("\\s+", " ").strip();
  }

  @Test
  void holdsTenUnsupportedFivePlainAndFiveHardSupportedPairs() throws Exception {
    Map<Object, Long> byKind =
        sample().stream().collect(Collectors.groupingBy(pair -> pair.get("kind"), Collectors.counting()));
    assertEquals(Map.of("unsupported", 10L, "supported", 5L, "hard-supported", 5L), byKind);
  }

  @Test
  void theLabelMatchesTheKind() throws Exception {
    for (Map<String, Object> pair : sample()) {
      boolean shouldBeSupported = !"unsupported".equals(pair.get("kind"));
      assertEquals(shouldBeSupported, pair.get("supported"), String.valueOf(pair.get("name")));
    }
  }

  @Test
  void everyPairIsCompleteAndNamesAreUnique() throws Exception {
    Set<Object> names = new HashSet<>();
    for (Map<String, Object> pair : sample()) {
      for (String key : List.of("name", "claim", "passage")) {
        assertTrue(
            pair.get(key) instanceof String text && !text.isBlank(),
            key + " missing in " + pair.get("name"));
      }
      assertTrue(names.add(pair.get("name")), "duplicate name " + pair.get("name"));
    }
  }

  @Test
  void everyPassageIsCopiedVerbatimFromTheDocs() throws Exception {
    StringBuilder docs = new StringBuilder();
    try (var files = Files.list(Path.of("docs"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".md")).toList()) {
        docs.append(collapse(Files.readString(file))).append(' ');
      }
    }
    for (Map<String, Object> pair : sample()) {
      assertTrue(
          docs.toString().contains(collapse((String) pair.get("passage"))),
          pair.get("name") + ": the passage is not text from docs/");
    }
  }
}
