package eval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CaseStateTest {
    private static final ExpectedFact F = new ExpectedFact("f", List.of("d#a"), List.of());
    @Test void unrecordedFactHasNoCoveringClaims() { assertEquals(List.of(), new CaseState().covering(F)); }
    @Test void recordedClaimsComeBack() {
        var s = new CaseState();
        var c = new Claim("x", List.of("d#a"));
        s.setCovering(F, List.of(c));
        assertEquals(List.of(c), s.covering(F));
    }
}
