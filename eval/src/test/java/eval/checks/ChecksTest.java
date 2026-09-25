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
  void noListMeansAllSixInRegistryOrder() {
    var all =
        Checks.registered(new KnowledgeBase(List.of()), new Judge((s, u) -> "YES"), null).stream()
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
    assertTrue(error("Refusal", "Nope").startsWith("unknown check(s): Nope (known: Refusal,"));
    assertEquals("'checks' must name at least one check", error());
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
    assertEquals(List.of("Refusal", "Coverage"), Checks.requested("uncited", null, null));
    assertEquals(
        List.of("Refusal", "Coverage", "Relevance"),
        Checks.requested("UNCITED", List.of("Relevance"), null));
    assertNull(Checks.requested(null, null, null));
    assertEquals(List.of("Source"), Checks.requested(null, null, List.of("Source")));
  }

  @Test
  void everyAppTypeIsClosedOverRequiresAndAddsNothingExtra() {
    for (String type : List.of("cited", "uncited", "smoke")) {
      List<String> floor = Checks.requested(type, null, null);
      assertEquals(floor, selected(floor.toArray(String[]::new)), type);
    }
  }

  @Test
  void badAppTypeCombinationsAreErrors() {
    assertTrue(
        assertThrows(
                IllegalArgumentException.class, () -> Checks.requested("nope", null, null))
            .getMessage()
            .contains("known: cited, smoke, uncited"));
    assertThrows(
        IllegalArgumentException.class, () -> Checks.requested("cited", null, List.of("Refusal")));
    assertThrows(
        IllegalArgumentException.class, () -> Checks.requested(null, List.of("Relevance"), null));
  }
}
