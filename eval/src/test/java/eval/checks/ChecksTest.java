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

  @Test
  void relevanceIsRegisteredLastAndIsAdvisory() {
    var registered =
        Checks.registered(new KnowledgeBase(List.of()), new Judge((system, user) -> "YES"));
    var last = registered.get(registered.size() - 1);
    assertEquals("Relevance", last.check().name());
    assertFalse(last.gating());
    for (var others : registered.subList(0, registered.size() - 1)) {
      assertTrue(others.gating(), others.check().name());
    }
  }

  private static List<String> selected(String... names) {
    return Checks.registered(
            new KnowledgeBase(List.of()), new Judge((system, user) -> "YES"), List.of(names))
        .stream()
        .map(registered -> registered.check().name())
        .toList();
  }

  private static String error(String... names) {
    return assertThrows(IllegalArgumentException.class, () -> selected(names)).getMessage();
  }

  @Test
  void registryHasAllSixInOrder() {
    var all =
        Checks.registered(new KnowledgeBase(List.of()), new Judge((s, u) -> "YES")).stream()
            .map(registered -> registered.check().name())
            .toList();
    assertEquals(names(), all);
    assertEquals(6, all.size());
  }

  @Test
  void aSubsetKeepsRegistryOrderAndIgnoresCaseAndDuplicates() {
    assertEquals(List.of("Refusal", "Coverage"), selected("coverage", "REFUSAL", "Coverage"));
  }

  @Test
  void unknownEmptyAndNoGatingCheckAreErrors() {
    assertTrue(error("Refusal", "Nope").startsWith("unknown check(s) in addChecks: Nope (known: Refusal,"));
    assertTrue(error("Relevance").contains("at least one gating check"));
  }

  @Test
  void groundednessDoesNotNeedCoverage() {
    assertEquals(List.of("Groundedness"), selected("Groundedness"));
  }

  @Test
  void aCheckPullsInTheCheckItNeeds() {
    assertEquals(List.of("Refusal", "Coverage", "Source"), selected("Refusal", "Source"));
  }

  @Test
  void appTypeIsTheFloorAndAddChecksAddsToIt() {
    assertEquals(List.of("Refusal", "Coverage"), Checks.requested("uncited", null));
    assertEquals(
        List.of("Refusal", "Coverage", "Relevance"),
        Checks.requested("UNCITED", List.of("Relevance")));
  }

  @Test
  void noAppTypeMeansCited() {
    assertEquals(Checks.requested("cited", null), Checks.requested(null, null));
    assertEquals(
        List.of("Refusal", "Citation integrity", "Coverage", "Groundedness", "Source", "Relevance"),
        Checks.requested(null, List.of("Relevance")));
  }

  @Test
  void everyAppTypeIsClosedOverRequiresAndAddsNothingExtra() {
    for (String type : List.of("cited", "uncited", "smoke")) {
      List<String> floor = Checks.requested(type, null);
      assertEquals(floor, selected(floor.toArray(String[]::new)), type);
    }
  }

  @Test
  void unknownAppTypeIsAnError() {
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> Checks.requested("nope", null))
            .getMessage()
            .contains("known: cited, smoke, uncited"));
  }
}
