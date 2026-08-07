package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Key;

/**
 * The half of the four AbstractGeneratedGlob32/64 that is identical in both flavours and at both mask
 * widths. It cannot go into AbstractGlob / AbstractMutableGlob with the rest : hashCode, equals and
 * toString are Object methods, which an interface may not implement as defaults, and the memoized
 * hashCode needs a field.
 * <p>
 * It deliberately says nothing about the isSet / isNull masks : those and their accessors stay declared
 * (and final) in the subclass, so a call on a generated Glob keeps resolving to a single implementation.
 */
abstract public class AbstractGeneratedGlob implements AbstractMutableGlob {
    private int hashCode;

    final public int hashCode() {
        if (hashCode != 0) {
            return hashCode;
        }
        int hashCode = getType().hashCode();
        for (Field keyField : getType().getKeyFields()) {
            Object value = getValue(keyField);
            hashCode = 31 * hashCode + (value != null ? keyField.valueHash(value) : 0);
        }
        if (hashCode == 0) {
            hashCode = 31;
        }
        this.hashCode = hashCode;
        return hashCode;
    }

    final public boolean isHashComputed() {
        return hashCode != 0;
    }

    public static void throwError(GlobType globType, Field field) {
        throw new RuntimeException(field.getFullName() + "(at index " + field.getIndex() + ")" + " invalid in " + globType.describe());
    }

    /** The default branch of the generated tableswitch : called by INVOKEVIRTUAL on the generated Glob. */
    final public void throwError(Field field) {
        throw new RuntimeException(field.getFullName() + "(at index " + field.getIndex() + ")" + " invalid in " + getType().describe());
    }

    final public String toString() {
        StringBuilder buffer = new StringBuilder();
        toString(buffer);
        return buffer.toString();
    }

    final public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (!Key.class.isAssignableFrom(o.getClass())) {
            return false;
        }

        Key otherKey = (Key) o;
        if (getType() != otherKey.getGlobType()) {
            return false;
        }

        Field[] keyFields = getType().getKeyFields();
        if (keyFields.length == 0) {
            return true; //o instanceof Glob && reallyEquals((Glob) o);
        }

        for (Field field : keyFields) {
            if (!field.valueEqual(getValue(field), otherKey.getValue(field))) {
                return false;
            }
        }
        return true;
    }
}
