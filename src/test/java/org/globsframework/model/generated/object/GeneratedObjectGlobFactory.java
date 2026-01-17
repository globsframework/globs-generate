package org.globsframework.model.generated.object;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.IntegerArrayField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.impl.DefaultGlobFactory;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.get.GlobGetIntAccessor;
import org.globsframework.model.generator.primitive.AsmGlobPrimitiveGenerator;

public class GeneratedObjectGlobFactory extends DefaultGlobFactory {
    public static final GlobType TYPE;

    public static final IntegerField f1;

    public static final IntegerArrayField f2;

    static {
        TYPE = AsmGlobPrimitiveGenerator.TYPE;
        f1 = (IntegerField) TYPE.findField("i1");
        f2 = (IntegerArrayField) TYPE.findField("i2");
    }

    public GeneratedObjectGlobFactory(GlobType type) {
        super(type);
    }

    public MutableGlob create() {
        return new GeneratedObjectGlob();
    }

    @Override
    public GlobGetIntAccessor getGetAccessor(IntegerField field) {
        return null;
//        return (GlobGetIntAccessor) GeneratedObjectGlob.getGetAccessor(field);
    }


    //    public <T extends FieldVisitor> T accept(T visitor) throws Exception {
//        f1.accept(visitor);
//        f2.accept(visitor);
//        return visitor;
//    }
//
//    public <T extends FieldVisitorWithContext<C>, C> T accept(T visitor, C context) throws Exception {
//        f1.accept(visitor, context);
//        f2.accept(visitor, context);
//        return visitor;
//    }
//
//    public <T extends FieldVisitorWithTwoContext<C, D>, C, D> T accept(T visitor, C ctx1, D ctx2) throws Exception {
//        f1.accept(visitor, ctx1, ctx2);
//        f2.accept(visitor, ctx1, ctx2);
//        return visitor;
//    }
//
//    public GlobSetAccessor getSetValueAccessor(Field field) {
//        return null;
//    }
//
//    public GlobGetAccessor getGetValueAccessor(Field field) {
//        return null;
//    }
}
