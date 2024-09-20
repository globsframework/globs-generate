package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.FieldValueVisitor;
import org.globsframework.core.model.FieldValues;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.impl.AbstractMutableGlob;
import org.globsframework.core.utils.exceptions.ItemNotFound;

abstract public class AbstractGeneratedGlob32 extends AbstractMutableGlob {
    int hashCode;
    int isSet;
    int isNull;

    public void setNull(int index) {
        isNull |= (1 << index);
    }

    public void setNotNull(int index) {
        isNull &= ~(1 << index);
    }

    public boolean isNull(int index) {
        return (isNull & (1L << index)) != 0;
    }

    public void setSetAt(int index) {
        isSet |= (1 << index);
    }

    public boolean isSetAt(int index) {
        return (isSet & (1L << index)) != 0;
    }

    public void clearSetAt(int index) {
        isSet &= ~(1 << index);
    }

    public boolean isSet(Field field) throws ItemNotFound {
        return isSetAt(field.getIndex());
    }

    public MutableGlob unset(Field field) {
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

    public <T extends FieldValues.Functor>
    T apply(T functor) throws Exception {
        for (Field field : getType().getFields()) {
            if (isSet(field)) {
                functor.process(field, doGet(field));
            }
        }
        return functor;
    }

    public <T extends FieldValueVisitor> T accept(T functor) throws Exception {
        for (Field field : getType().getFields()) {
            if (isSet(field)) { //  || field.isKeyField()
                field.accept(functor, doGet(field));
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

}
