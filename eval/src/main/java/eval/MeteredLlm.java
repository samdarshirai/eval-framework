package eval;

import llm.Completion;
import llm.Llm;

/**
 * Passes every call to the real {@code Llm} and records it in the meter under the running check.
 */
final class MeteredLlm implements Llm {
  private final Llm delegate;
  private final UsageMeter meter;

  MeteredLlm(Llm delegate, UsageMeter meter) {
    this.delegate = delegate;
    this.meter = meter;
  }

  @Override
  public String complete(String system, String user) {
    Completion completion = delegate.completeWithUsage(system, user);
    meter.judgeCall(completion.promptTokens(), completion.completionTokens());
    return completion.text();
  }
}
