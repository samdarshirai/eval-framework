package eval.checks;

import java.util.List;

/** The single registration list. Add a check: one class plus one line here. Order is run order. */
public final class Checks {
    public static List<Registered> registered() {
        return List.of(
            new Registered(new RefusalCheck(), true));
    }
}
