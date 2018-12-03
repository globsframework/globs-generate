package org.globsframework.model.generator;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.model.impl.AbstractMutableGlob;

abstract public class AbstractGeneratedGlob extends AbstractMutableGlob {

    public void throwError(Field field) {
        throw new RuntimeException(field.getFullName() + "(at index " + field.getIndex() + ")" + " invalid in " + getType().describe());
    }

    public static void throwError(GlobType globType, Field field) {
        throw new RuntimeException(field.getFullName() + "(at index " + field.getIndex() + ")" + " invalid in " + globType.describe());
    }

}
