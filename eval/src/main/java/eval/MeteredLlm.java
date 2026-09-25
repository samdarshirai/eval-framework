package eval;

import llm.Completion;
import llm.Llm;

/**
 * Passes every call to the real {@code Llm} and records it in the meter under the running check.
 */
final class MeteredLlm implements Llm {
  private final Llm delegate;
  private final UsageMeter meter;
  private final DebugLog debug;

  MeteredLlm(Llm delegate, UsageMeter meter) {
    this(delegate, meter, DebugLog.OFF);
  }

  MeteredLlm(Llm delegate, UsageMeter meter, DebugLog debug) {
    this.delegate = delegate;
    this.meter = meter;
    this.debug = debug;
  }

  @Override
  public String complete(String system, String user) {
    long startedNanos = System.nanoTime();
    Completion completion;
    try {
      completion = delegate.completeWithUsage(system, user);
    } catch (RuntimeException e) {
      debug.log(
          "judge call failed in " + meter.currentCheck() + ": " + DebugLog.clip(e.getMessage()));
      throw e;
    }
    meter.judgeCall(completion.promptTokens(), completion.completionTokens());
    debug.log(
        "judge call in "
            + meter.currentCheck()
            + ": "
            + (System.nanoTime() - startedNanos) / 1_000_000
            + " ms, tokens "
            + completion.promptTokens()
            + " in / "
            + completion.completionTokens()
            + " out, prompt "
            + (system.length() + user.length())
            + " chars, reply: "
            + DebugLog.clip(completion.text()));
    return completion.text();
  }
}
