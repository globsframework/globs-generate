package org.globsframework.model.generated;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.fields.*;
import org.globsframework.model.FieldValues;
import org.globsframework.model.MutableGlob;
import org.globsframework.model.generator.AbstractGeneratedGlobFactory;
import org.globsframework.model.generator.AsmGlobGenerator;
import org.globsframework.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.model.globaccessor.set.GlobSetAccessor;

import java.io.InputStream;

public class GeneratedGlobFactory extends AbstractGeneratedGlobFactory {
    public final static GlobType GLOB_TYPE;
    public final static IntegerField f1;
    public final static StringField f2;
    public final static DoubleField f3;
    public final static IntegerArrayField f4;
    public final static StringField f5;

    static {
        GLOB_TYPE = AsmGlobGenerator.TYPE;
        f1 = (IntegerField) GLOB_TYPE.getField("f1");
        f2 = (StringField) GLOB_TYPE.getField("f2");
        f3 = (DoubleField) GLOB_TYPE.getField("f3");
        f4 = (IntegerArrayField) GLOB_TYPE.getField("f4");
        f5 = (StringField) GLOB_TYPE.getField("f5");
    }

    public MutableGlob create() {
        return new GeneratedGlob();
    }


//    // l'appelant lit la taille et austela taille du buffer pour qu'on n'est plus de if sur la taille.
//    public MutableGlob read(GlobReader globReader) {
//        GeneratedGlob generatedGlob = new GeneratedGlob();
//        int nullFlag_1 = globReader.readInt();
//        if ((nullFlag_1 & 0x1) == 0) {
//            generatedGlob.i1 = globReader.readInt();
//        }
//        if ((nullFlag_1 & 0x2) == 0) {
//            generatedGlob.i3 = Double.longBitsToDouble(globReader.readLong());
//        }
//        generatedGlob.i2 = globReader.readString();
//
//        return generatedGlob;
//    }
//
//    public interface GlobReader {
//        void readHead(int size);
//
//        int readInt();
//
//        long readLong();
//
//        String readString();
//    }

    public <T extends FieldVisitor> T accept(T visitor) throws Exception {
        visitor.visitInteger(f1);
        visitor.visitString(f2);
        visitor.visitDouble(f3);
        visitor.visitIntegerArray(f4);
        return visitor;
    }

    public <T extends FieldVisitorWithContext<C>, C> T accept(T visitor, C context) throws Exception {
        visitor.visitInteger(f1, context);
        visitor.visitString(f2, context);
        visitor.visitDouble(f3, context);
        visitor.visitIntegerArray(f4, context);
        return visitor;
    }

    public <T extends FieldVisitorWithTwoContext<C, D>, C, D> T accept(T visitor, C ctx1, D ctx2) throws Exception{
        visitor.visitInteger(f1, ctx1, ctx2);
        visitor.visitString(f2, ctx1, ctx2);
        visitor.visitDouble(f3, ctx1, ctx2);
        visitor.visitIntegerArray(f4, ctx1, ctx2);
        return visitor;
    }

//    interface Walker {
//        boolean nextAndIsNull();
//
//        int readInt();
//
//        double readDouble();
//
//        String readString();
//
//        double[] readDoubleArray();
//
//
//    }

    public static <T extends FieldValues.Functor>
    T processValue(GeneratedGlob generated, T visitor) throws Exception {
        visitor.process(f1, generated.get_i1());
        visitor.process(f2, generated.i2);
        visitor.process(f3, generated.get_i3());
        visitor.process(f4, generated.ia1);
        visitor.process(f5, generated.get_name());
        return visitor;
    }

    public static <T extends FieldValueVisitor>
    T acceptValueStatic(GeneratedGlob generated, T visitor) throws Exception {
        visitor.visitInteger(f1, generated.get_i1() );
        visitor.visitString(f2, generated.i2);
        visitor.visitDouble(f3, generated.get_i3());
        visitor.visitIntegerArray(f4, generated.ia1);
        return visitor;
    }

    public GlobSetAccessor getSetValueAccessor(Field field) {
        return GeneratedGlob.getSetAccessor(field);
    }

    public GlobGetAccessor getGetValueAccessor(Field field) {
        return GeneratedGlob.getGetAccessor(field);
    }

}
