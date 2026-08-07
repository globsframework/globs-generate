package org.globsframework.model.generator.primitive;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.utils.exceptions.ItemNotFound;
import org.globsframework.model.generator.AbstractGeneratedGlob;

/**
 * Base of the generated Globs of a type with 33 to 64 fields, primitive flavour : the values live in
 * native fields, so null needs a mask of its own. It starts all ones — every doSet writes the null bit
 * before the value, so a field that was never set reads as null.
 */
abstract public class AbstractGeneratedGlob64 extends AbstractGeneratedGlob {
    // public, like the value fields of the generated subclass : the generated accessors GETFIELD the masks
    // straight out of another package to answer isSet / isNull without a call.
    public long isSet;
    public long isNull = 0xFFFFFFFFFFFFFFFFL;

    final public void setNull(int index) {
        isNull |= (1L << index);
    }

    final public void setNotNull(int index) {
        isNull &= ~(1L << index);
    }

    final public boolean isNull(int index) {
        return (isNull & (1L << index)) != 0;
    }

    final public void setSetAt(int index) {
        isSet |= (1L << index);
    }

    final public boolean isSetAt(int index) {
        return (isSet & (1L << index)) != 0;
    }

    final public void clearSetAt(int index) {
        isSet &= ~(1L << index);
    }

    final public boolean isSet(Field field) throws ItemNotFound {
        return isSetAt(field.getIndex());
    }
}
