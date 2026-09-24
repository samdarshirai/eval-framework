package eval;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SuiteReportTest {
    /** n cases; ids in failing set fail; ids starting "oos" get category out-of-scope. */
    private SuiteReport report(int n, Set<Integer> failing, Set<Integer> oos) {
        List<CaseResult> cs = new ArrayList<>();
        for (int i = 0; i < n; i++)
            cs.add(new CaseResult((oos.contains(i) ? "oos-" : "c-") + i, "q", oos.contains(i) ? "out-of-scope" : "single-source",
                null, new Answer(false, List.of()), !failing.contains(i), null, null, List.of()));
        return new SuiteReport("r", "http://x", 0.90, List.of(), cs);
    }

    @Test void twentySixOfTwentyEightMeetsFloor() {
        var r = report(28, Set.of(0, 1), Set.of());
        assertEquals(0, r.exitCode());
    }
    @Test void twentyFiveOfTwentyEightMissesFloorAndSaysWhy() {
        var r = report(28, Set.of(0, 1, 2), Set.of());
        assertEquals(1, r.exitCode());
        assertTrue(r.exitReasons().get(0).contains("89.3%") && r.exitReasons().get(0).contains("90.0%"));
    }
    @Test void nineOfTenIsExactlyOnTheFloor() { assertEquals(0, report(10, Set.of(0), Set.of()).exitCode()); }

    @Test void oneFailingOutOfScopeFailsRunEvenWhenRateIsHigh() {
        var r = report(28, Set.of(27), Set.of(27));
        assertEquals(1, r.exitCode());
        assertEquals(List.of("out-of-scope case failed: oos-27"), r.exitReasons());
    }
    @Test void nonOutOfScopeFailuresAloneDoNotTriggerTheOutOfScopeRule() {
        assertEquals(List.of(), report(28, Set.of(5), Set.of(27)).exitReasons());
    }
}
