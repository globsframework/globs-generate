package org.globsframework.model.generated;

import org.globsframework.core.model.Glob;
import org.globsframework.core.model.caller.FromGlobCallerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A pair of caller interfaces for the from-Glob tests that only need <em>a</em> shape — the naming, the
 * ClassLoader and the mask-shape ones, which care about the emitted class and about what it read, not about
 * what it called.
 * <p>
 * There is no generic pair in core any more : a caller is emitted over the interfaces its codec owns, so a
 * test that wants one has to bring its own, exactly like a codec does. Core only fixes the head of each
 * method — the Glob here, {@code isSet, isNull, value} there.
 */
public final class FromGlobShapes {

    private FromGlobShapes() {
    }

    /** What the pass carries, after each method's fixed head. */
    public static final Class<?>[] ARGS = {List.class};

    /** What the emitted class implements. */
    public interface Walk {
        void walk(Glob data, List<String> seen);
    }

    /** What it calls — named differently on purpose : the two are matched on their parameters. */
    public interface FieldWalk {
        void onField(boolean isSet, boolean isNull, Object value, List<String> seen);
    }

    /** One line per field, which is what a caller and the loop have to agree on to the character. */
    public static FromGlobCallerFactory.Functions<FieldWalk> recorder() {
        return field -> {
            String name = field.getName();
            return (isSet, isNull, value, seen) -> seen.add(name + "|" + isSet + "|" + isNull + "|" + value);
        };
    }

    public static List<String> trace(Walk caller, Glob glob) {
        List<String> seen = new ArrayList<>();
        caller.walk(glob, seen);
        return seen;
    }
}
