package org.globsframework.model.generator;

import org.globsframework.core.model.Glob;

/**
 * Applies the {@link FieldValueFunction}s it was built from to every field of a Glob, in one unrolled pass.
 * <p>
 * The Glob must be one the type's own factory produced : the generated implementation reads the fields and
 * the masks of the generated Glob class directly, so anything else is a ClassCastException.
 */
public interface GeneratedFunctionCaller<D, E> {
    void call(Glob data, D ctx1, E ctx2);
}
