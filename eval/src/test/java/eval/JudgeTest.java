package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeTest {
  private static Judge saying(String reply) {
    return new Judge((system, user) -> reply);
  }

  private static final List<Claim> CLAIMS =
      List.of(
          new Claim("a", List.of("d#a")),
          new Claim("b", List.of("d#a")),
          new Claim("c", List.of("d#a")));

  @Test
  void agreeAcceptsYesInAnyCaseWithPunctuation() {
    assertTrue(saying("YES").agree("f", "c"));
    assertTrue(saying("Yes.").agree("f", "c"));
    assertTrue(saying("YES - the claim matches").agree("f", "c"));
  }

  @Test
  void agreeAcceptsNo() {
    assertFalse(saying("no").agree("f", "c"));
    assertFalse(saying("NO.").agree("f", "c"));
  }

  @Test
  void agreeAcceptsMarkdownOrQuotesAroundTheWord() {
    assertTrue(saying("**YES**").agree("f", "c"));
    assertFalse(saying("`no`").agree("f", "c"));
    assertTrue(saying("\"Yes\"").agree("f", "c"));
  }

  @Test
  void agreeRejectsUnclearOutputInsteadOfGuessing() {
    assertThrows(
        IllegalStateException.class, () -> saying("I think it is probably fine").agree("f", "c"));
    assertThrows(IllegalStateException.class, () -> saying("Not sure").agree("f", "c"));
    assertThrows(IllegalStateException.class, () -> saying("").agree("f", "c"));
    assertThrows(IllegalStateException.class, () -> new Judge((s, u) -> null).agree("f", "c"));
  }

  @Test
  void agreePromptCarriesFactAndClaim() {
    var seen = new String[1];
    new Judge(
            (s, u) -> {
              seen[0] = u;
              return "YES";
            })
        .agree("Safari 14 is supported", "Safari 13 is supported");
    assertTrue(
        seen[0].contains("Safari 14 is supported") && seen[0].contains("Safari 13 is supported"),
        seen[0]);
  }

  @Test
  void coveringReturnsZeroBasedIndices() {
    assertEquals(List.of(0, 2), saying("[1, 3]").covering("f", CLAIMS));
  }

  @Test
  void coveringEmptyListMeansNotCovered() {
    assertEquals(List.of(), saying("[]").covering("f", CLAIMS));
  }

  @Test
  void coveringToleratesTextAroundTheArray() {
    assertEquals(List.of(1), saying("Claims: [2]").covering("f", CLAIMS));
  }

  @Test
  void coveringRejectsReplyWithTwoArrays() {
    assertThrows(
        IllegalStateException.class,
        () -> saying("None state it; claims [1] and [2] contradict it").covering("f", CLAIMS));
  }

  @Test
  void coveringRejectsOutOfRangeAndGarbage() {
    assertThrows(IllegalStateException.class, () -> saying("[4]").covering("f", CLAIMS));
    assertThrows(IllegalStateException.class, () -> saying("[0]").covering("f", CLAIMS));
    assertThrows(IllegalStateException.class, () -> saying("none of them").covering("f", CLAIMS));
    assertThrows(IllegalStateException.class, () -> saying("").covering("f", CLAIMS));
  }

  @Test
  void coveringPromptNumbersClaimsFromOne() {
    var seen = new String[1];
    new Judge(
            (s, u) -> {
              seen[0] = u;
              return "[]";
            })
        .covering("f", CLAIMS);
    assertTrue(seen[0].contains("1. a") && seen[0].contains("3. c"), seen[0]);
  }
}
