package org.globsframework.model.generator;

import org.globsframework.core.metamodel.fields.Field;

/**
 * Superclass of the generated set accessors. The generated subclass implements the typed set() (plus
 * setNative() where it pays) by writing the Glob field directly and maintaining the isSet / isNull bits.
 */
public abstract class AbstractGeneratedSetAccessor {
    protected final Field field;

    protected AbstractGeneratedSetAccessor(Field field) {
        this.field = field;
    }
}
