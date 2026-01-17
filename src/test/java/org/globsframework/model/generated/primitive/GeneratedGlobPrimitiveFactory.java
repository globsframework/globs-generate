package org.globsframework.model.generated.primitive;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.IntegerArrayField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.model.GlobFactory;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.model.generator.primitive.AsmGlobPrimitiveGenerator;

public class GeneratedGlobPrimitiveFactory implements GlobFactory {
    public static final GlobType TYPE;

    public static final IntegerField f1;

    public static final IntegerArrayField f2;

    static {
        TYPE = AsmGlobPrimitiveGenerator.TYPE;
        f1 = (IntegerField) TYPE.findField("i1");
        f2 = (IntegerArrayField) TYPE.findField("i2");
    }

    public MutableGlob create(Object context) {
        return new GeneratedGlobPrimitiveGlob();
    }

    @Override
    public GlobType getGlobType() {
        return TYPE;
    }

    @Override
    public GlobSetAccessor getSetValueAccessor(Field field) {
        return GeneratedGlobPrimitiveGlob.getSetAccessor(field);
    }

    @Override
    public GlobGetAccessor getGetValueAccessor(Field field) {
        return GeneratedGlobPrimitiveGlob.getGetAccessor(field);
    }
}
