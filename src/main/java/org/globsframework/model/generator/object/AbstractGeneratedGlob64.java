package org.globsframework.model.generator.object;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.utils.exceptions.ItemNotFound;
import org.globsframework.model.generator.AbstractGeneratedGlob;

/** Base of the generated Globs of a type with 33 to 64 fields : one long mask, null is the value null. */
abstract public class AbstractGeneratedGlob64 extends AbstractGeneratedGlob {
    // public, like the value fields of the generated subclass : the generated accessors GETFIELD the mask
    // straight out of another package to answer isSet / isNull without a call.
    public long isSet;

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
