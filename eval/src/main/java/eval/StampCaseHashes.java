package eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes {@code confirmed_hash} into the case files so nobody computes a hash by hand (D21, unit
 * 22). Run it after checking that each case still holds against the docs:
 *
 * <pre>java -cp eval/target/eval.jar eval.StampCaseHashes [--config team-config.yaml]</pre>
 *
 * It edits text lines and does not re-serialize the YAML, so comments and quoting survive.
 */
public final class StampCaseHashes {
  private static final String CASE_START = "- id:";
  private static final String HASH_LINE = "  confirmed_hash:";

  private StampCaseHashes() {}

  public static void main(String[] args) throws Exception {
    EvalConfig config = EvalConfig.from(Path.of("."), CliArgs.parse(args));
    KnowledgeBase knowledgeBase = KnowledgeBase.from(config);
    List<EvalCase> cases =
        EvalCaseLoader.load(config.casesDir(), knowledgeBase, config.categories());
    Map<String, String> hashByCaseId = new HashMap<>();
    for (EvalCase evalCase : cases) {
      String hash = CaseHash.of(evalCase, knowledgeBase);
      if (hash != null) {
        hashByCaseId.put(evalCase.id(), hash);
      }
    }
    List<Path> caseFiles;
    try (var files = Files.list(config.casesDir())) {
      caseFiles =
          files
              .filter(file -> file.toString().endsWith(".yaml") || file.toString().endsWith(".yml"))
              .sorted()
              .toList();
    }
    Set<String> foundIds = new HashSet<>();
    for (Path caseFile : caseFiles) {
      foundIds.addAll(caseIdsIn(Files.readString(caseFile)));
    }
    try {
      requireEveryIdFound(hashByCaseId.keySet(), foundIds);
    } catch (IllegalArgumentException problem) {
      System.out.println("ERROR: " + problem.getMessage());
      System.exit(2);
    }
    int filesChanged = 0;
    for (Path caseFile : caseFiles) {
      String before = Files.readString(caseFile);
      String after = stamp(before, hashByCaseId);
      if (!after.equals(before)) {
        Files.writeString(caseFile, after);
        filesChanged++;
      }
    }
    System.out.println(
        "stamped " + hashByCaseId.size() + " case(s), " + filesChanged + " file(s) changed");
  }

  /** The ids of the cases written as a {@code - id: <id>} line at column 0. */
  static Set<String> caseIdsIn(String yaml) {
    Set<String> ids = new HashSet<>();
    yaml.lines()
        .forEach(
            line -> {
              String id = idOf(line);
              if (id != null) {
                ids.add(id);
              }
            });
    return ids;
  }

  /**
   * Throws when a case that needs a hash has no {@code - id:} line to stamp under (a case written
   * with the id not first, or inline as {@code - {id: ...}}), instead of stamping it wrongly or not
   * at all.
   */
  static void requireEveryIdFound(Set<String> hashedIds, Set<String> foundIds) {
    Set<String> missing = new TreeSet<>(hashedIds);
    missing.removeAll(foundIds);
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "cannot find a '- id: <id>' line at column 0 to stamp under for: "
              + String.join(", ", missing)
              + ". Write each case with its id first, or add confirmed_hash by hand.");
    }
  }

  /**
   * The case id on a {@code - id: <id>} line (quotes and a trailing comment removed), else null.
   */
  private static String idOf(String line) {
    if (!line.startsWith(CASE_START)) {
      return null;
    }
    return line.substring(CASE_START.length())
        .replaceAll("\\s+#.*$", "")
        .trim()
        .replaceAll("^[\"']|[\"']$", "");
  }

  /**
   * Returns the file text with {@code confirmed_hash: "<hash>"} as the last line of each case named
   * in {@code hashByCaseId}, replacing an existing one. Other cases and lines are unchanged.
   */
  static String stamp(String yaml, Map<String, String> hashByCaseId) {
    List<String> lines = new ArrayList<>(yaml.lines().toList());
    List<Integer> caseStarts = new ArrayList<>();
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      // Any list item at column 0 ends the previous case, whatever key it starts with.
      if (lines.get(lineIndex).startsWith("- ") || lines.get(lineIndex).equals("-")) {
        caseStarts.add(lineIndex);
      }
    }
    // Last case first, so an insertion never shifts a case start that is still to be processed.
    for (int caseIndex = caseStarts.size() - 1; caseIndex >= 0; caseIndex--) {
      int start = caseStarts.get(caseIndex);
      int end = caseIndex + 1 < caseStarts.size() ? caseStarts.get(caseIndex + 1) : lines.size();
      String caseId = idOf(lines.get(start));
      String hash = caseId == null ? null : hashByCaseId.get(caseId);
      if (hash == null) {
        continue;
      }
      List<String> block = lines.subList(start, end);
      block.removeIf(line -> line.startsWith(HASH_LINE));
      int lastContentLine = block.size() - 1;
      while (lastContentLine > 0 && block.get(lastContentLine).isBlank()) {
        lastContentLine--;
      }
      block.add(lastContentLine + 1, HASH_LINE + " \"" + hash + "\"");
    }
    return String.join("\n", lines) + "\n";
  }
}
