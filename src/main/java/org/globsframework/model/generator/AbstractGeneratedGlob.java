package org.globsframework.model.generator;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.model.MutableGlob;
import org.globsframework.model.impl.AbstractMutableGlob;

abstract public class AbstractGeneratedGlob extends AbstractMutableGlob {

    public static void throwError(GlobType globType, Field field) {
        throw new RuntimeException(field.getFullName() + "(at index " + field.getIndex() + ")" + " invalid in " + globType.describe());
    }

    public void throwError(Field field) {
        throw new RuntimeException(field.getFullName() + "(at index " + field.getIndex() + ")" + " invalid in " + getType().describe());
    }

    public MutableGlob duplicate() {
        MutableGlob instantiate = getType().instantiate();
        for (Field field : getType().getFields()) {
            instantiate.setValue(field, getValue(field));
        }
        return instantiate;
    }
}
