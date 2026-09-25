package eval.checks;

import eval.Judge;
import eval.KnowledgeBase;
import java.util.List;

/** The single registration list. Add a check: one class plus one line here. Order is run order. */
public final class Checks {
  public static List<Registered> registered(KnowledgeBase kb, Judge judge) {
    return List.of(
        new Registered(new RefusalCheck(), true),
        new Registered(new CitationIntegrityCheck(kb), true),
        new Registered(new CoverageCheck(judge), true),
        new Registered(new GroundednessCheck(kb, judge), true),
        new Registered(new SourceCheck(), true),
        new Registered(new RelevanceCheck(judge), false));
  }
}
