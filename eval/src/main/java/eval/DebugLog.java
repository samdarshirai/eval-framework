package eval;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The {@code --debug} log: one {@code [debug]} line per event, on the same stream as the report.
 * {@link #OFF} logs nothing, so callers never check a flag.
 */
final class DebugLog {
  static final DebugLog OFF = new DebugLog(null);

  private static final int CLIP = 200;
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  private final PrintStream out;

  DebugLog(PrintStream out) {
    this.out = out;
  }

  void log(String message) {
    if (out != null) {
      out.println("[debug] " + TIME.format(LocalTime.now()) + " " + message);
    }
  }

  /** One line, at most 200 characters: keeps question, answer and judge text readable. */
  static String clip(String text) {
    if (text == null) {
      return "null";
    }
    String oneLine = text.replaceAll("\\s+", " ").strip();
    return oneLine.length() <= CLIP ? oneLine : oneLine.substring(0, CLIP) + "…";
  }
}
