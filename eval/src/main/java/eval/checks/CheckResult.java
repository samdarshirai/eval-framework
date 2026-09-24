package eval.checks;

public record CheckResult(boolean passed, String reason) {
    public static CheckResult ok() { return new CheckResult(true, null); }
    public static CheckResult fail(String reason) { return new CheckResult(false, reason); }
}
