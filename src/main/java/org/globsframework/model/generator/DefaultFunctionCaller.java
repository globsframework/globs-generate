package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;

/**
 * The {@link GeneratedFunctionCaller} of a type that has no generated Glob class : the plain loop over a
 * table of functions indexed by {@code Field.getIndex()}.
 * <p>
 * Behaviourally identical to the generated one — same functions, same order, and the same isSet / isNull /
 * value, isNull being what {@code getValue} answers null for. What it does not give is the point of
 * generating: one call site for the whole loop, seeing every function class it is ever handed, i.e. exactly
 * the megamorphic dispatch the generated caller exists to remove. It is the fallback, not an alternative.
 * <p>
 * Unlike the generated caller it accepts any Glob of the type, whoever built it.
 */
public class DefaultFunctionCaller<D, E> implements GeneratedFunctionCaller<D, E> {
    private final GlobType type;
    private final Field[] fields;
    private final FieldValueFunction<Object, D, E>[] functions;

    @SuppressWarnings("unchecked")
    public DefaultFunctionCaller(GlobType type, GenerateCaller.GetFieldValueFunction<D, E> getFieldValueFunction) {
        this.type = type;
        Field[] typeFields = type.getFields();
        fields = new Field[typeFields.length];
        functions = new FieldValueFunction[typeFields.length];
        for (Field field : typeFields) {
            FieldValueFunction<Object, D, E> function =
                    (FieldValueFunction<Object, D, E>) getFieldValueFunction.create(field);
            if (function == null) {
                throw new IllegalArgumentException("No FieldValueFunction for " + field.getName()
                                                   + " of " + type.getName());
            }
            fields[field.getIndex()] = field;
            functions[field.getIndex()] = function;
        }
    }

    public void call(Glob data, D ctx1, E ctx2) {
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            Object value = data.getValue(field);
            functions[i].call(data.isSet(field), value == null, value, ctx1, ctx2);
        }
    }

    public GlobType getType() {
        return type;
    }
}
