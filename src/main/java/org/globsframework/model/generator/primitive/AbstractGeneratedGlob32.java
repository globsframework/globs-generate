package org.globsframework.model.generator.primitive;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.*;
import org.globsframework.core.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.core.model.impl.AbstractMutableGlob;
import org.globsframework.core.model.utils.FieldCheck;
import org.globsframework.core.utils.exceptions.ItemNotFound;

abstract public class AbstractGeneratedGlob32 implements AbstractMutableGlob {
    int hashCode;
    protected int isSet;
    protected int isNull = 0xFFFFFFFF;
    int reserve = 0;

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

    public <T extends Functor>
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
                field.acceptValue(functor, doGet(field));
            }
        }
        return functor;
    }

    public <CTX, T extends FieldValueVisitorWithContext<CTX>> T accept(T functor, CTX ctx) throws Exception {
        for (Field field : getType().getFields()) {
            final int index = field.getIndex();
            if (isSetAt(index)) {
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
            return true; //o instanceof Glob && reallyEquals((Glob) o); ; return false?
        }

        for (Field field : keyFields) {
            if (!field.valueEqual(getValue(field), otherKey.getValue(field))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void checkReserved() {
        if (FieldCheck.CheckGlob.shouldCheck) {
            if (reserve < 0) {
                throw new ReservationException("Data not reserved");
            }
        }
    }

    @Override
    public void reserve(int key) {
        if (key <= 0) {
            throw new ReservationException("Reserved key <= 0 Got " + key);
        }
        if (reserve > 0) {
            throw new ReservationException("Already reserved by " + key);
        }
        reserve = key;
    }

    @Override
    public boolean release(int key) {
        if (key <= 0) {
            throw new ReservationException("Released key <= 0 Got " + key);
        }
        if (reserve == -key) {
            return false;
        }
        if (reserve != 0) {
            if (reserve != key) {
                throw new ReservationException("Can not release data : reserved by " + reserve + " != " + key);
            }
            reserve = -key;
        } else {
            throw new ReservationException("Can not release not own Glob '" + key + "'");
        }
        hashCode = 0;
        isSet = 0;
        isNull = 0;
        return true;
    }

    @Override
    public void unReserve() {
        hashCode = 0;
        reserve = 0;
    }

    @Override
    public boolean isReserved() {
        return reserve > 0;
    }

    @Override
    public boolean isReservedBy(int key) {
        return key > 0 && reserve == key;
    }

    @Override
    public void checkWasReservedBy(int key) {
        if (key <= 0 || reserve != -key) {
            throw new ReservationException("Data was not reserved by " + reserve + " != " + key);
        }
    }

    public static abstract class AbstractGlobGetNativeAccessor implements GlobGetAccessor {
        final int valueSet;

        public AbstractGlobGetNativeAccessor(Field field) {
            valueSet = 1 << field.getIndex();
        }

        public boolean isSet(Glob glob) {
            final AbstractGeneratedGlob32 typeData = (AbstractGeneratedGlob32) glob;
            typeData.checkReserved();
            return (typeData.isSet & valueSet) != 0;
        }

        public boolean isNull(Glob glob) {
            final AbstractGeneratedGlob32 typeData = (AbstractGeneratedGlob32) glob;
            typeData.checkReserved();
            return uncheckedIsNull(typeData);
        }

        public boolean uncheckedIsNull(AbstractGeneratedGlob32 typeData) {
            return (typeData.isNull & valueSet) != 0;
        }
    }

}
