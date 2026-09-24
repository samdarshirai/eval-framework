package eval.checks;

import eval.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RefusalCheckTest {
    private final RefusalCheck check = new RefusalCheck();
    private EvalCase c(String behavior) {
        return new EvalCase("c", "q", "x", null, behavior, List.of(), "authored", "o", "2026-09-24");
    }
    private static final Claim CL = new Claim("c", List.of("d#a"));

    @Test void expectedRefuseAndRefusedPasses() { assertTrue(check.run(c("refuse"), new Answer(true, List.of())).passed()); }
    @Test void expectedAnswerAndAnsweredPasses() { assertTrue(check.run(c("answer"), new Answer(false, List.of(CL))).passed()); }

    @Test void confidentAnswerToOutOfScopeIsHallucination() {
        var r = check.run(c("refuse"), new Answer(false, List.of(CL)));
        assertFalse(r.passed());
        assertTrue(r.reason().startsWith("hallucination"));
    }
    @Test void refusingACoveredQuestionIsOverRefusal() {
        var r = check.run(c("answer"), new Answer(true, List.of()));
        assertFalse(r.passed());
        assertTrue(r.reason().startsWith("over-refusal"));
    }
    @Test void refusedWithClaimsIsContractViolation() {
        var r = check.run(c("refuse"), new Answer(true, List.of(CL)));
        assertFalse(r.passed());
        assertTrue(r.reason().startsWith("contract violation"));
    }
    @Test void refusalIsFirstAndGatingInTheRegistrationList() {
        var first = Checks.registered().get(0);
        assertEquals("Refusal", first.check().name());
        assertTrue(first.gating());
    }
}
