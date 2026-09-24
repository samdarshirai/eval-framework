package eval.checks;

import eval.*;
import java.util.*;

/**
 * Deterministic (D11): each covered fact must be backed by a claim that cites a chunk of the same
 * document as one of the fact's gold chunks. Document-level on purpose: a different chunk of the
 * right document is fine (that is Groundedness's business), citing only some other document is not.
 * A fact Coverage did not cover is skipped, Coverage already fails that.
 */
public final class SourceCheck implements Check {
  @Override
  public String name() {
    return "Source";
  }

  @Override
  public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
    List<String> problems = new ArrayList<>();
    for (ExpectedFact fact : evalCase.facts()) {
      List<Claim> covering = state.covering(fact);
      if (covering.isEmpty()) {
        continue;
      }
      Set<String> goldDocuments = new LinkedHashSet<>();
      for (String chunkId : fact.chunks()) {
        goldDocuments.add(documentOf(chunkId));
      }
      Set<String> citedDocuments = new LinkedHashSet<>();
      for (Claim claim : covering) {
        if (claim.citations() != null) {
          for (String chunkId : claim.citations()) {
            citedDocuments.add(documentOf(chunkId));
          }
        }
      }
      if (citedDocuments.stream().noneMatch(goldDocuments::contains)) {
        problems.add(
            "\""
                + fact.fact()
                + "\" is covered only by claims citing "
                + citedDocuments
                + "; expected a chunk of "
                + goldDocuments);
      }
    }
    return problems.isEmpty() ? CheckResult.ok() : CheckResult.fail(String.join("; ", problems));
  }

  /** The document behind a chunk ID: the text before the first '#'. */
  private static String documentOf(String chunkId) {
    int hash = chunkId.indexOf('#');
    return hash < 0 ? chunkId : chunkId.substring(0, hash);
  }
}
