package org.globsframework.model.generated.primitive;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.IntegerArrayField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.impl.DefaultGlobFactory;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.model.generator.primitive.AsmGlobPrimitiveGenerator;

public class GeneratedGlobPrimitiveFactory extends DefaultGlobFactory {
    public static final GlobType TYPE;

    public static final IntegerField f1;

    public static final IntegerArrayField f2;

    static {
        TYPE = AsmGlobPrimitiveGenerator.TYPE;
        f1 = (IntegerField) TYPE.findField("i1");
        f2 = (IntegerArrayField) TYPE.findField("i2");
    }

    public GeneratedGlobPrimitiveFactory() {
        super(TYPE);
    }

    public MutableGlob create() {
        return new GeneratedGlobPrimitiveGlob();
    }
}
