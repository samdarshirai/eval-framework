package eval;

import java.util.List;

public record Answer(boolean refused, List<Claim> claims) {}
