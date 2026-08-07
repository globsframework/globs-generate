package org.globsframework.model.generator.primitive;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.core.utils.exceptions.ItemNotFound;
import org.globsframework.model.generator.AbstractGeneratedGlob;

/**
 * Base of the generated Globs of a type with at most 32 fields, primitive flavour : the values live in
 * native fields, so null needs a mask of its own. It starts all ones — every doSet writes the null bit
 * before the value, so a field that was never set reads as null.
 */
abstract public class AbstractGeneratedGlob32 extends AbstractGeneratedGlob {
    // public, like the value fields of the generated subclass : the generated accessors GETFIELD the masks
    // straight out of another package to answer isSet / isNull without a call.
    public int isSet;
    public int isNull = 0xFFFFFFFF;

    final public void setNull(int index) {
        isNull |= (1 << index);
    }

    final public void setNotNull(int index) {
        isNull &= ~(1 << index);
    }

    final public boolean isNull(int index) {
        return (isNull & (1 << index)) != 0;
    }

    final public void setSetAt(int index) {
        isSet |= (1 << index);
    }

    final public boolean isSetAt(int index) {
        return (isSet & (1 << index)) != 0;
    }

    final public void clearSetAt(int index) {
        isSet &= ~(1 << index);
    }

    final public boolean isSet(Field field) throws ItemNotFound {
        return isSetAt(field.getIndex());
    }

    public static abstract class AbstractGlobGetNativeAccessor implements GlobGetAccessor {
        final int valueSet;

        public AbstractGlobGetNativeAccessor(Field field) {
            valueSet = 1 << field.getIndex();
        }

        public boolean isSet(Glob glob) {
            final AbstractGeneratedGlob32 typeData = (AbstractGeneratedGlob32) glob;
            return (typeData.isSet & valueSet) != 0;
        }

        public boolean isNull(Glob glob) {
            final AbstractGeneratedGlob32 typeData = (AbstractGeneratedGlob32) glob;
            return uncheckedIsNull(typeData);
        }

        public boolean uncheckedIsNull(AbstractGeneratedGlob32 typeData) {
            return (typeData.isNull & valueSet) != 0;
        }
    }
}
