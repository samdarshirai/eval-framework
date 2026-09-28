# Eval Cases: How they were Selected, Where they are Sourced From, What is Missing, How they Grow Without Becoming Unmanageable

## How I selected the Cases

First I considered when LLMs would likely fail and based upon this consideration developed my questions. Next I used my questions to identify five categories of cases for which I wrote all the cases.

| Category | What Test | Number |
|---|---|---|
| Single Source | A piece of information from a single document. Information includes exact values (e.g. a version number) | 8 |
| Multiple Sources | An answer to a question requiring at least two documents | 6 |
| False Premise | An inaccurate assumption regarding a feature mentioned within the documentation. The assistant should correct this incorrect assumption, not accept it, nor refuse to address it. | 6 |
| Outside Scope | The documentation cannot provide an answer. Therefore, the assistant must decline to answer (two sub-categories include "unrelated" and "plausible-nonexistent") | 5 |
| Edge Case | Precision for exact value(s), multiple questions asked simultaneously, light paraphrasing | 3 |

## Where the Cases Came From

The cases came from two distinct sources:

- **Authored** – I authored all of the cases by reviewing Usercentrics documentation related to Browser Support, A/B Testing, Geolocation Rules, Google Consent Mode, and TCF 2.2.
- **Derived from Runs** – One case (`ss-tcf-cmp-version`) was created from the run that demonstrated the problem: `ms-tcf-version-and-consent-default` did not indicate which CMP version is required under TCF. As a result, one-half of the case was separated from the original run to form a new run-source case.

Each case includes `owner` and `added` fields. These fields will enable someone to contact the person responsible for checking stale-looking cases. Additionally, each case includes a `confirmed_hash` field on its gold to detect drift due to subsequent revisions made to the documentation.

## What I Intentionally Excluded

| Excluded |
|---
| Partial Answers |
| Underspecified Questions |
| Large Vocabulary Mismatches |
| Refusal Wording |
| Multi-turn Conversations, Questions in Languages Other Than English |
| Adversarial / Prompt Injection Questions |
| Documentation Located Beyond the Five Selected Pages |
| Cases for HyDE or Reranking |


## How the Eval Set Grows Without Becoming Unmanageable

Four rules, all inexpensive to follow:

1. **Hard Limit Per Category.** When adding a new case, you must either remove an existing case or combine it with another case. This is a practice that limits the size of the set by limiting the number of cases per category; the loader does not enforce this limitation. This prevents the set from growing indefinitely.
2. **New Cases Are Generated Based on Real World Failures and Corrected Support Issues, Not by Attempting to Generate Cases for Every Single Page of Documentation.** For instance, `ss-tcf-cmp-version` is an example of how a run revealed a gap in coverage. The gap then became a case, and `ss-loader-chrome` (a redundant case that probed a table already covered by two other cases) was removed to create space for the new case.
3. **Each Case Records Source, Owner, and Added Date.** An evaluation case with no apparent purpose for existence or no one remembering why it was included in the set is what ultimately destroys large evaluation sets; these fields provide visibility into such cases rather than allowing them to silently rot.
4. **Staleness is Detected Automatically, Not Through Memory.** If a gold chunk referenced in a case no longer exists when loaded into the eval set, it results in an immediate error. If a document referenced in a case has been revised since its `confirmed_hash` field was last updated, it generates a warning message prior to calling an assistant function. The warning message identifies the case that needs attention. Adding or updating a case involves merely modifying data (one YAML entry), and therefore does not require altering the harness itself.

The eval set is intendedly incomplete. There are several examples of duplicate coverage (three cases probing the same browser support table; two multi-source cases referencing the same gold chunks). The duplicates are identified as potential candidates for consolidation if/when additional cases are needed for slots. They are not allowed to accumulate silently.