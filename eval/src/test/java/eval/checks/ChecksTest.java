package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.Judge;
import eval.KnowledgeBase;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChecksTest {
  private static List<String> names() {
    return Checks.registered(new KnowledgeBase(List.of()), new Judge((system, user) -> "YES"))
        .stream()
        .map(registered -> registered.check().name())
        .toList();
  }

  @Test
  void groundednessRunsAfterCitationIntegrityAndCoverage() {
    List<String> names = names();
    assertTrue(names.indexOf("Citation integrity") < names.indexOf("Coverage"));
    assertTrue(names.indexOf("Coverage") < names.indexOf("Groundedness"));
  }

  @Test
  void sourceRunsAfterGroundedness() {
    List<String> names = names();
    assertTrue(names.indexOf("Groundedness") < names.indexOf("Source"));
  }

  @Test
  void groundednessAndSourceGate() {
    var registered =
        Checks.registered(new KnowledgeBase(List.of()), new Judge((system, user) -> "YES"));
    for (var check : registered) {
      if (check.check().name().equals("Groundedness") || check.check().name().equals("Source")) {
        assertTrue(check.gating(), check.check().name());
      }
    }
  }
}
