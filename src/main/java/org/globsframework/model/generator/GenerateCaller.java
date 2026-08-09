package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;

/**
 * Generates a {@link GeneratedFunctionCaller} for one GlobType. Reachable from the outside by casting the
 * type's factory : {@code type.getGlobFactory() instanceof GlobGenerateFactory f}.
 * <p>
 * Every call to {@link #create} emits a brand new class, holding <em>these</em> functions in its static
 * finals — that is what makes each call site monomorphic, and it is also why this belongs to the setup phase
 * of a serializer or a codec, not to its hot path.
 */
public interface GenerateCaller {

    <D, E>
    GeneratedFunctionCaller<D, E> create(GetFieldValueFunction<D, E> getFieldValueFunction);

    /**
     * The caller of any type, generated or not : the generated class when the type has one, a
     * {@link DefaultFunctionCaller} otherwise. Callers get the same behaviour either way and never have to
     * carry a second code path — only the speed differs.
     * <p>
     * A type has no generated class when the module is not installed at all ({@code globs.builder} unset),
     * when it asks for {@code mode none}, or when it has more than 64 fields.
     */
    static <D, E> GeneratedFunctionCaller<D, E> callerFor(GlobType type, GetFieldValueFunction<D, E> getFieldValueFunction) {
        return type.getGlobFactory() instanceof GlobGenerateFactory generate
                ? generate.create(getFieldValueFunction)
                : new DefaultFunctionCaller<>(type, getFieldValueFunction);
    }

    /** Called once per field, at generation time, to get the function that field will be handled with. */
    interface GetFieldValueFunction<D, E> {
        <T> FieldValueFunction<T, D, E> create(Field field);
    }
}
