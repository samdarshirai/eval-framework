package eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    int filesChanged = 0;
    try (var caseFiles = Files.list(config.casesDir())) {
      for (Path caseFile :
          caseFiles.filter(file -> file.toString().endsWith(".yaml")).sorted().toList()) {
        String before = Files.readString(caseFile);
        String after = stamp(before, hashByCaseId);
        if (!after.equals(before)) {
          Files.writeString(caseFile, after);
          filesChanged++;
        }
      }
    }
    System.out.println(
        "stamped " + hashByCaseId.size() + " case(s), " + filesChanged + " file(s) changed");
  }

  /**
   * Returns the file text with {@code confirmed_hash: "<hash>"} as the last line of each case named
   * in {@code hashByCaseId}, replacing an existing one. Other cases and lines are unchanged.
   */
  static String stamp(String yaml, Map<String, String> hashByCaseId) {
    List<String> lines = new ArrayList<>(yaml.lines().toList());
    List<Integer> caseStarts = new ArrayList<>();
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      if (lines.get(lineIndex).startsWith(CASE_START)) {
        caseStarts.add(lineIndex);
      }
    }
    // Last case first, so an insertion never shifts a case start that is still to be processed.
    for (int caseIndex = caseStarts.size() - 1; caseIndex >= 0; caseIndex--) {
      int start = caseStarts.get(caseIndex);
      int end = caseIndex + 1 < caseStarts.size() ? caseStarts.get(caseIndex + 1) : lines.size();
      String caseId =
          lines.get(start).substring(CASE_START.length()).trim().replaceAll("^[\"']|[\"']$", "");
      String hash = hashByCaseId.get(caseId);
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
