package eval;

public record CheckOutcome(String check, boolean gating, boolean passed, String reason) {}
