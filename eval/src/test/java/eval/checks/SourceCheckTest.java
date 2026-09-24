package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SourceCheckTest {
  private static final ExpectedFact GEO =
      new ExpectedFact(
          "Do not combine GDPR and TCF in one rule",
          List.of("geolocation-rules#constraints"),
          List.of("TCF"));
  private static final ExpectedFact GTAG =
      new ExpectedFact(
          "Consent Mode requires gtag.js or Google Tag Manager",
          List.of("consent-mode#prerequisites"),
          List.of("gtag.js"));

  private static EvalCase caseWith(ExpectedFact... facts) {
    return new EvalCase(
        "c", "q", "multi-source", null, "answer", List.of(facts), null, null, null);
  }

  private static CheckResult run(EvalCase evalCase, CaseState state) {
    return new SourceCheck().run(evalCase, new Answer(false, List.of()), state);
  }

  private static CaseState covered(ExpectedFact fact, Claim... claims) {
    var state = new CaseState();
    state.setCovering(fact, List.of(claims));
    return state;
  }

  @Test
  void claimCitingTheGoldDocumentPasses() {
    var state = covered(GEO, new Claim("c", List.of("geolocation-rules#constraints")));
    assertTrue(run(caseWith(GEO), state).passed());
  }

  @Test
  void claimCitingAnotherChunkOfTheGoldDocumentPasses() {
    var state = covered(GEO, new Claim("c", List.of("geolocation-rules#overview")));
    assertTrue(run(caseWith(GEO), state).passed());
  }

  @Test
  void claimCitingOnlyAnotherDocumentFailsAndNamesBothDocuments() {
    var state = covered(GEO, new Claim("c", List.of("consent-mode#prerequisites")));
    var result = run(caseWith(GEO), state);
    assertFalse(result.passed());
    assertTrue(result.reason().contains(GEO.fact()), result.reason());
    assertTrue(result.reason().contains("consent-mode"), result.reason());
    assertTrue(result.reason().contains("geolocation-rules"), result.reason());
  }

  @Test
  void oneCoveringClaimFromTheGoldDocumentIsEnough() {
    var state =
        covered(
            GEO,
            new Claim("wrong doc", List.of("consent-mode#prerequisites")),
            new Claim("right doc", List.of("geolocation-rules#constraints")));
    assertTrue(run(caseWith(GEO), state).passed());
  }

  @Test
  void aClaimCitingTheGoldDocumentAndAnotherDocumentPasses() {
    var state =
        covered(
            GEO,
            new Claim(
                "c", List.of("consent-mode#prerequisites", "geolocation-rules#constraints")));
    assertTrue(run(caseWith(GEO), state).passed());
  }

  @Test
  void multiSourceCaseFailsWhenOnlyOneFactUsesItsDocument() {
    var state = new CaseState();
    var geolocationClaim = new Claim("c", List.of("geolocation-rules#constraints"));
    state.setCovering(GEO, List.of(geolocationClaim));
    state.setCovering(GTAG, List.of(geolocationClaim)); // right for GEO, wrong document for GTAG
    var result = run(caseWith(GEO, GTAG), state);
    assertFalse(result.passed());
    assertTrue(result.reason().contains(GTAG.fact()), result.reason());
    assertFalse(result.reason().contains(GEO.fact()), result.reason());
  }

  @Test
  void aFactCoverageDidNotCoverIsSkipped() {
    assertTrue(run(caseWith(GEO), new CaseState()).passed());
  }

  @Test
  void aChunkIdWithoutAHashIsItsOwnDocument() {
    var plain = new ExpectedFact("f", List.of("plain"), List.of());
    var state = covered(plain, new Claim("c", List.of("plain")));
    assertTrue(run(caseWith(plain), state).passed());
  }

  @Test
  void aCoveringClaimWithNullCitationsFailsWithoutThrowing() {
    var state = covered(GEO, new Claim("c", null));
    assertFalse(run(caseWith(GEO), state).passed());
  }
}
