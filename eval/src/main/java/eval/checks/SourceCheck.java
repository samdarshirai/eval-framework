package eval.checks;

import eval.*;
import java.util.*;

/**
 * Deterministic (D11): did the answer use the document the fact comes from? For each fact that
 * Coverage found a claim for, some covering claim must cite a chunk of the same document as one of
 * the fact's gold chunks. Example: a fact from {@code geolocation-rules#constraints} is backed by a
 * claim citing {@code geolocation-rules#overview}, but not by one citing only {@code
 * consent-mode#prerequisites}.
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
      List<Claim> coveringClaims = state.covering(fact);
      if (coveringClaims.isEmpty()) {
        continue; // Coverage already fails an uncovered fact
      }
      Set<String> expectedDocuments = documentsOf(fact.chunks());
      Set<String> citedDocuments = new LinkedHashSet<>();
      for (Claim claim : coveringClaims) {
        citedDocuments.addAll(documentsOf(claim.citations()));
      }
      if (Collections.disjoint(citedDocuments, expectedDocuments)) {
        problems.add(
            "\""
                + fact.fact()
                + "\" is covered only by claims citing "
                + citedDocuments
                + "; expected a chunk of "
                + expectedDocuments);
      }
    }
    return problems.isEmpty() ? CheckResult.ok() : CheckResult.fail(String.join("; ", problems));
  }

  /** The documents behind chunk IDs (the text before the first '#'); null means none. */
  private static Set<String> documentsOf(List<String> chunkIds) {
    Set<String> documents = new LinkedHashSet<>();
    if (chunkIds == null) {
      return documents;
    }
    for (String chunkId : chunkIds) {
      int hash = chunkId.indexOf('#');
      documents.add(hash < 0 ? chunkId : chunkId.substring(0, hash));
    }
    return documents;
  }
}
