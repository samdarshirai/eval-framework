package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CaseStateTest {
  private static final ExpectedFact FACT = new ExpectedFact("f", List.of("d#a"), List.of());

  @Test
  void unrecordedFactHasNoCoveringClaims() {
    assertEquals(List.of(), new CaseState().covering(FACT));
  }

  @Test
  void recordedClaimsComeBack() {
    var state = new CaseState();
    var claim = new Claim("x", List.of("d#a"));
    state.setCovering(FACT, List.of(claim));
    assertEquals(List.of(claim), state.covering(FACT));
  }
}
