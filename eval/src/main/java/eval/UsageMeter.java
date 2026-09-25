package eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts what a run spends (D17): judge calls and tokens per check, assistant calls and time. It
 * only counts calls that came back, so the numbers are a floor on spend: a call that failed carries
 * no usage from the provider.
 */
final class UsageMeter {
  /** USD per million tokens, from the {@code judgePricing} setting. */
  record Pricing(double inputPerMillion, double outputPerMillion) {
    double cost(long promptTokens, long completionTokens) {
      return (promptTokens * inputPerMillion + completionTokens * outputPerMillion) / 1_000_000.0;
    }
  }

  private static final String OUTSIDE_CHECKS = "outside checks";

  private final Map<String, long[]> judgeByCheck =
      new LinkedHashMap<>(); // {calls, prompt, completion}
  private int assistantCalls;
  private long assistantMillis;
  // ponytail: one current check, right while cases run one at a time; parallel cases need a
  // ThreadLocal here.
  private String currentCheck = OUTSIDE_CHECKS;

  /** Names the check whose judge calls are counted next; null means no check is running. */
  void setCheck(String check) {
    currentCheck = check == null ? OUTSIDE_CHECKS : check;
  }

  void assistantCall(long millis) {
    assistantCalls++;
    assistantMillis += millis;
  }

  void judgeCall(long promptTokens, long completionTokens) {
    long[] totals = judgeByCheck.computeIfAbsent(currentCheck, check -> new long[3]);
    totals[0]++;
    totals[1] += promptTokens;
    totals[2] += completionTokens;
  }

  /** The measurements so far; {@code pricing} may be null, which leaves the cost null. */
  SuiteReport.Usage usage(long wallClockMillis, Pricing pricing) {
    List<SuiteReport.Usage.CheckUsage> byCheck = new ArrayList<>();
    judgeByCheck.forEach(
        (check, totals) ->
            byCheck.add(
                new SuiteReport.Usage.CheckUsage(check, (int) totals[0], totals[1], totals[2])));
    long promptTokens =
        byCheck.stream().mapToLong(SuiteReport.Usage.CheckUsage::promptTokens).sum();
    long completionTokens =
        byCheck.stream().mapToLong(SuiteReport.Usage.CheckUsage::completionTokens).sum();
    Double cost = pricing == null ? null : pricing.cost(promptTokens, completionTokens);
    return new SuiteReport.Usage(wallClockMillis, assistantCalls, assistantMillis, byCheck, cost);
  }
}
