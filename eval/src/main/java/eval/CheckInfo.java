package eval;

/** A registered check as it ran in this suite; the same for every case. */
public record CheckInfo(String name, boolean gating) {}
