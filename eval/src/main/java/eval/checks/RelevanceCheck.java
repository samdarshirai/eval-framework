package eval.checks;

import eval.*;
import java.util.*;

/**
 * Is each claim pertinent to the question, not true-but-off-topic padding? One judge call per
 * claim. Advisory (D12): the harness registers it with gating=false, so a failure here is shown and
 * counted but never fails a case. A refusal has no claims and costs no call.
 */
public final class RelevanceCheck implements Check {
  private final Judge judge;

  public RelevanceCheck(Judge judge) {
    this.judge = judge;
  }

  @Override
  public String name() {
    return "Relevance";
  }

  @Override
  public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
    List<Claim> claims = answer.claims() == null ? List.of() : answer.claims();
    List<String> offTopic = new ArrayList<>();
    for (Claim claim : claims) {
      if (!judge.relevant(evalCase.question(), claim.claim())) {
        offTopic.add("\"" + claim.claim() + "\"");
      }
    }
    if (offTopic.isEmpty()) {
      return CheckResult.ok();
    }
    return CheckResult.fail("off-topic claim(s): " + String.join("; ", offTopic));
  }
}
