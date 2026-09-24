package eval;

/** One check's verdict on one case. Which checks exist and whether they gate is in SuiteReport.checks, once per run. */
public record CheckOutcome(String check, boolean passed, String reason) {}
