package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelevanceCheckTest {
  private static final CaseState STATE = new CaseState();

  private static EvalCase evalCase() {
    return new EvalCase(
        "c",
        "Which browsers are supported?",
        "single-source",
        null,
        "answer",
        List.of(),
        "authored",
        "o",
        "2026-09-25");
  }

  private static Answer answerOf(String... claimTexts) {
    List<Claim> claims = new ArrayList<>();
    for (String claimText : claimTexts) {
      claims.add(new Claim(claimText, List.of("d#a")));
    }
    return new Answer(false, claims);
  }

  @Test
  void aRefusalHasNoClaimsAndMakesNoJudgeCall() {
    int[] judgeCalls = new int[1];
    RelevanceCheck check =
        new RelevanceCheck(
            new Judge(
                (system, user) -> {
                  judgeCalls[0]++;
                  return "NO";
                }));
    CheckResult result = check.run(evalCase(), new Answer(true, List.of()), STATE);
    assertTrue(result.passed());
    assertEquals(0, judgeCalls[0]);
  }

  @Test
  void aNullClaimListIsTreatedAsNoClaims() {
    RelevanceCheck check = new RelevanceCheck(new Judge((system, user) -> "NO"));
    assertTrue(check.run(evalCase(), new Answer(false, null), STATE).passed());
  }

  @Test
  void everyClaimRelevantPasses() {
    RelevanceCheck check = new RelevanceCheck(new Judge((system, user) -> "YES"));
    assertTrue(
        check.run(evalCase(), answerOf("Safari 14 is supported", "Chrome is"), STATE).passed());
  }

  @Test
  void aNoIsFlaggedByNameAndTheOtherClaimsAreNot() {
    RelevanceCheck check =
        new RelevanceCheck(
            new Judge((system, user) -> user.contains("padding claim") ? "NO" : "YES"));
    CheckResult result =
        check.run(evalCase(), answerOf("Safari 14 is supported", "padding claim"), STATE);
    assertFalse(result.passed());
    assertTrue(result.reason().contains("padding claim"), result.reason());
    assertFalse(result.reason().contains("Safari 14"), result.reason());
  }

  @Test
  void theJudgeIsAskedAboutTheCasesQuestion() {
    List<String> prompts = new ArrayList<>();
    RelevanceCheck check =
        new RelevanceCheck(
            new Judge(
                (system, user) -> {
                  prompts.add(user);
                  return "YES";
                }));
    check.run(evalCase(), answerOf("Safari 14 is supported"), STATE);
    assertEquals(1, prompts.size());
    assertTrue(prompts.get(0).contains("Which browsers are supported?"), prompts.get(0));
  }

  @Test
  void aJudgeErrorPropagatesSoTheRunnerRecordsItAsACheckError() {
    RelevanceCheck check = new RelevanceCheck(new Judge((system, user) -> "Maybe"));
    assertThrows(
        IllegalStateException.class, () -> check.run(evalCase(), answerOf("a claim"), STATE));
  }
}
