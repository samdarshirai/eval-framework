package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JudgeRelevantTest {
  private static Judge saying(String reply) {
    return new Judge((system, user) -> reply);
  }

  @Test
  void yesMeansRelevantInAnyCaseWithPunctuation() {
    assertTrue(saying("YES").relevant("question", "claim"));
    assertTrue(saying("Yes.").relevant("question", "claim"));
    assertTrue(saying("**YES**").relevant("question", "claim"));
  }

  @Test
  void noMeansOffTopic() {
    assertFalse(saying("NO").relevant("question", "claim"));
    assertFalse(saying("no - this is about pricing").relevant("question", "claim"));
  }

  @Test
  void anythingElseIsAnErrorNeverASilentNo() {
    assertThrows(IllegalStateException.class, () -> saying("Maybe").relevant("question", "claim"));
    assertThrows(IllegalStateException.class, () -> saying("").relevant("question", "claim"));
    assertThrows(IllegalStateException.class, () -> saying(null).relevant("question", "claim"));
  }

  @Test
  void promptCarriesTheQuestionAndTheClaim() {
    AtomicReference<String> seen = new AtomicReference<>();
    new Judge(
            (system, user) -> {
              seen.set(user);
              return "YES";
            })
        .relevant("Which browsers are supported?", "Safari 14 is supported");
    assertTrue(seen.get().contains("Which browsers are supported?"), seen.get());
    assertTrue(seen.get().contains("Safari 14 is supported"), seen.get());
  }
}
