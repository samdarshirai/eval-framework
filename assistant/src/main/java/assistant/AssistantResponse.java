package assistant;

import java.util.List;

public record AssistantResponse(boolean refused, List<Claim> claims) {}
