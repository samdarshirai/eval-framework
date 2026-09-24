package eval.checks;

import eval.*;
import java.util.*;

/**
 * Deterministic (D26): every claim has at least one citation and every cited chunk ID exists in the
 * knowledge base.
 */
public final class CitationIntegrityCheck implements Check {
  private final KnowledgeBase kb;

  public CitationIntegrityCheck(KnowledgeBase kb) {
    this.kb = kb;
  }

  @Override
  public String name() {
    return "Citation integrity";
  }

  @Override
  public CheckResult run(EvalCase c, Answer a, CaseState state) {
    List<String> problems = new ArrayList<>();
    for (Claim claim : a.claims() == null ? List.<Claim>of() : a.claims()) {
      if (claim.citations() == null || claim.citations().isEmpty()) {
        problems.add("no citation for claim: \"" + claim.claim() + "\"");
        continue;
      }
      for (String id : claim.citations()) {
        if (!kb.has(id)) {
          problems.add("fabricated citation: " + id + " (claim: \"" + claim.claim() + "\")");
        }
      }
    }
    return problems.isEmpty() ? CheckResult.ok() : CheckResult.fail(String.join("; ", problems));
  }
}
