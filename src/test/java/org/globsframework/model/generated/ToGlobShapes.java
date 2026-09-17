package org.globsframework.model.generated;

import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.KeySource;

/**
 * A pair of caller interfaces for the tests that only need <em>a</em> shape — the naming and the ClassLoader
 * ones, which care about the emitted class and not about what it calls.
 * <p>
 * There is no generic pair in core any more : a to-Glob caller is emitted over the interfaces its own caller
 * owns, so a test that wants one has to bring its own, exactly like a parser does.
 */
public final class ToGlobShapes {

    private ToGlobShapes() {
    }

    /** The parameter types the two interfaces below share, which is what ties them together. */
    public static final Class<?>[] ARGS = {MutableGlob.class, Input.class};

    /** The input : both what says the next key and what a function would read from. */
    public static class Input implements KeySource {
        public int nextKey() {
            return -1;
        }
    }

    /** What the emitted class implements. */
    public interface Caller {
        void read(MutableGlob data, Input in);
    }

    /** What it calls — named differently on purpose : the two are matched on their parameters. */
    public interface Function {
        void readField(MutableGlob data, Input in);
    }

    /** The same shape over two other types : what the emitted class is named after includes which. */
    public interface OtherCaller {
        void read(MutableGlob data, Input in);
    }

    public interface OtherFunction {
        void readField(MutableGlob data, Input in);
    }
}
