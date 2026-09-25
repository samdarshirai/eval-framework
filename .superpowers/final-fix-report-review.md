# Review fixes report
- RED: new LabeledSampleTest.aMapWithPassagesAndAliasesLoadsAndRuns failed with ClassCastException (Map cannot be cast to List) with the old list-only loader; GREEN after loader accepts a `pairs` map.
- Grep for labeledSupported, .judged, AGREE/COVERING/SUPPORTS_SYSTEM (without _PROMPT), yesNo, goldGrounded, EXCERPT_LENGTH, describe( : no matches outside docs/superpowers/plans.
- Full `mvn -B test`: 168 tests, 0 failures, BUILD SUCCESS. gjf re-run on changed java files.
