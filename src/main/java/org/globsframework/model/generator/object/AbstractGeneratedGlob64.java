package org.globsframework.model.generator.object;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.FieldValueVisitor;
import org.globsframework.core.metamodel.fields.FieldValueVisitorWithContext;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.exceptions.ItemNotFound;
import org.globsframework.model.generator.AbstractMutableGlob;

abstract public class AbstractGeneratedGlob64 implements AbstractMutableGlob {
    private int hashCode;
    // public, like the value fields of the generated subclass : the generated accessors GETFIELD the mask
    // straight out of another package to answer isSet / isNull without a call.
    public long isSet;

    public void setSetAt(int index) {
        isSet |= (1L << index);
    }

    public boolean isSetAt(int index) {
        return (isSet & (1L << index)) != 0;
    }

    public void clearSetAt(int index) {
        isSet &= ~(1L << index);
    }

    public boolean isSet(Field field) throws ItemNotFound {
        return isSetAt(field.getIndex());
    }

    public MutableGlob unset(Field field) {
        doSet(field, null);
        clearSetAt(field.getIndex());
        return this;
    }

    public int hashCode() {
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

    public boolean isHashComputed() {
        return hashCode != 0;
    }

    // Looped fallbacks : used only when AsmGlobObjectGenerator.UNROLL_VISITORS is off, the generated
    // class overrides both otherwise.
    public <T extends Functor>
    T apply(T functor) throws Exception {
        for (Field field : getType().getFields()) {
            if (isSetAt(field.getIndex())) {
                functor.process(field, doGet(field));
            }
        }
        return functor;
    }

    public <T extends FieldValueVisitor> T accept(T functor) throws Exception {
        for (Field field : getType().getFields()) {
            if (isSetAt(field.getIndex())) {
                field.acceptValue(functor, doGet(field));
            }
        }
        return functor;
    }

    public <CTX, T extends FieldValueVisitorWithContext<CTX>> T accept(T functor, CTX ctx) throws Exception {
        for (Field field : getType().getFields()) {
            if (isSetAt(field.getIndex())) {
                field.acceptValue(functor, doGet(field), ctx);
            }
        }
        return functor;
    }

    public static void throwError(GlobType globType, Field field) {
        throw new RuntimeException(field.getFullName() + "(at index " + field.getIndex() + ")" + " invalid in " + globType.describe());
    }

    public void throwError(Field field) {
        throw new RuntimeException(field.getFullName() + "(at index " + field.getIndex() + ")" + " invalid in " + getType().describe());
    }


    public String toString() {
        StringBuilder buffer = new StringBuilder();
        toString(buffer);
        return buffer.toString();
    }

    public boolean equals(Object o) {
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
