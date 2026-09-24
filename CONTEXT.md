# Context

Glossary for the Usercentrics eval harness. Terms only, no implementation details.

## Terms

**Claim**
One atomic factual assertion made by an assistant, paired with the evidence (citations) offered for it. An assistant's answer consists *only* of claims. There is no free-text answer alongside them, so everything the assistant asserts is checkable.

**Citation**
A reference from a claim to the specific chunk of the knowledge base that supposedly supports it.

**Refusal**
The assistant declining to answer because the knowledge base doesn't cover the question. It is signalled separately from claims. A refusal contains no claims.

**Chunk**
A section of a knowledge-base document, identified by document name plus section heading (e.g. `consent-mode#default-consent-states`). Identified by heading, never by position, so IDs survive re-chunking.

**Expected fact**
A key point an answer must contain, written in plain language and listed on an eval case. Carries a list of *gold chunks*.

**Gold chunk**
A chunk a human has verified supports an expected fact. A fact's gold chunks are alternatives: citing *any one* of them is enough. Citing a chunk outside the list is not automatically wrong; it just isn't pre-verified.

**Gating check / Advisory check**
A gating check can fail an eval case. An advisory check is reported and counted but never fails a case. Relevance is advisory until its judge is calibrated.

**Baseline**
A previous run's results, promoted as the known-good reference that later runs are compared against.

**Regression**
An eval case that passed in the baseline and fails now. Any regression fails the run, even if the overall pass rate is above the floor. Separately, any failing out-of-scope case fails the run.

**Partially answerable question** (known gap, not tested)
A question the knowledge base answers in part (e.g. setup is documented, cost is not). The assistant has no way to say "here is what the docs cover, and I can't answer the rest". Left open as a design decision, alongside under-specified questions. Eval cases are authored to be fully answerable or fully out-of-scope.

**Risk tier**
A level assigned to an app that fixes its required checks and minimum pass floor. Every app starts in the top tier. Lowering it requires a written reason and platform sign-off at onboarding, and a change in the app's audience triggers re-review. Departments may raise their floor above the tier's minimum, never lower it.

**Expected behaviour**
Declared per eval case: either *answer* or *refuse*. Not implied by the case's category.

**False premise**
A question built on a wrong assumption about a feature the knowledge base *does* cover. Expected behaviour is *answer*: the assistant must correct the premise with cited claims. Going along with the premise is a failure. Refusing is also a failure, because the docs do cover the topic.

**Out-of-scope**
A question the knowledge base cannot answer. Expected behaviour is *refuse*. Has two subtypes:
- *unrelated*: outside the product docs entirely (pricing, legal advice).
- *plausible-nonexistent*: sounds like a real product feature but isn't in the docs. The hardest hallucination bait.
