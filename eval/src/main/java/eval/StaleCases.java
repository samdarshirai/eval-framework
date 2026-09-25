package eval;

import java.util.ArrayList;
import java.util.List;

/** Finds cases whose gold chunks changed since they were confirmed (D21, unit 22). */
final class StaleCases {
  private StaleCases() {}

  /**
   * One warning line per stale case, in case order. A case is stale when it has a confirmed hash
   * that differs from the current fingerprint of its gold chunks. A case with no hash, or with no
   * gold chunks, is never stale.
   */
  static List<String> warnings(List<EvalCase> cases, KnowledgeBase knowledgeBase) {
    List<String> warnings = new ArrayList<>();
    for (EvalCase evalCase : cases) {
      String confirmedHash = evalCase.confirmedHash();
      String currentHash = CaseHash.of(evalCase, knowledgeBase);
      if (confirmedHash == null || currentHash == null || confirmedHash.equals(currentHash)) {
        continue;
      }
      warnings.add(
          "case '"
              + evalCase.id()
              + "': its gold chunks changed since it was confirmed (confirmed_hash "
              + confirmedHash
              + ", now "
              + currentHash
              + "). Check the expected facts still hold, then re-stamp with"
              + " eval.StampCaseHashes.");
    }
    return warnings;
  }
}
