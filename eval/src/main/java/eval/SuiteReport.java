package eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record SuiteReport(String runId, String endpoint, double passFloor, List<CaseResult> cases) {
    public static final String OUT_OF_SCOPE = "out-of-scope";

    public long passed() { return cases.stream().filter(CaseResult::passed).count(); }

    @JsonProperty("passRate")
    public double passRate() { return cases.isEmpty() ? 0 : passed() / (double) cases.size(); }

    @JsonProperty("exitReasons")
    public List<String> exitReasons() {
        List<String> reasons = new ArrayList<>();
        if (passRate() < passFloor)
            reasons.add(String.format("pass rate %.1f%% is below floor %.1f%%", passRate() * 100, passFloor * 100));
        List<String> oos = cases.stream().filter(c -> !c.passed() && OUT_OF_SCOPE.equals(c.category())).map(CaseResult::id).toList();
        if (!oos.isEmpty()) reasons.add("out-of-scope case failed: " + String.join(", ", oos));
        return reasons;
    }

    public int exitCode() { return exitReasons().isEmpty() ? 0 : 1; }
}
