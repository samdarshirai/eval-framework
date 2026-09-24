package eval;

/** The system under test: answers one question, tagged with the run id. */
interface Assistant {
  Answer ask(String question, String runId) throws Exception;
}
