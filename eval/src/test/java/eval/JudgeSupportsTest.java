package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JudgeSupportsTest {
  private static Judge saying(String reply) {
    return new Judge((system, user) -> reply);
  }

  @Test
  void yesMeansSupportedInAnyCaseWithPunctuation() {
    assertTrue(saying("YES").supports("claim", "passage"));
    assertTrue(saying("Yes.").supports("claim", "passage"));
    assertTrue(saying("**YES**").supports("claim", "passage"));
  }

  @Test
  void noMeansNotSupported() {
    assertFalse(saying("NO").supports("claim", "passage"));
    assertFalse(saying("no - the passage says 13").supports("claim", "passage"));
  }

  @Test
  void anythingElseIsAnErrorNeverASilentNo() {
    assertThrows(IllegalStateException.class, () -> saying("Maybe").supports("claim", "passage"));
    assertThrows(IllegalStateException.class, () -> saying("").supports("claim", "passage"));
    assertThrows(IllegalStateException.class, () -> saying(null).supports("claim", "passage"));
  }

  @Test
  void thePromptCarriesThePassageAndTheClaim() {
    var seen = new AtomicReference<String>();
    var judge =
        new Judge(
            (system, user) -> {
              seen.set(user);
              return "YES";
            });
    judge.supports("Safari 14 works", "Safari: 14");
    assertTrue(seen.get().contains("Passage:\nSafari: 14"), seen.get());
    assertTrue(seen.get().contains("Claim: Safari 14 works"), seen.get());
  }
}
