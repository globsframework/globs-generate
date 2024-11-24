package org.globsframework.model.generated.object;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.FieldValueVisitor;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.model.generator.object.AbstractGeneratedGlob32;

/*
org.objectweb.asm.util.ASMifier .../globs-generate/target/test-classes/org/globsframework/model/generated/object/GeneratedObjectGlob.class
 */

public class GeneratedObjectGlob extends AbstractGeneratedGlob32 {
    private Integer i1;
    private int[] ia1;
    private String i2;
    private String[] ia2;
    private Double i3;
    private byte[] name;
    private Long l1;
    private long[] la1;
    private Glob gl;
    private Glob[] gla;
    private Boolean b;


    final public MutableGlob doSet(Field field, Object value) {
        final int index = field.getIndex();
        setSetAt(index);
        switch (index) {
            case 1:
                i1 = (Integer) value;
                break;
            case 2:
                i2 = (String) value;
                break;
            case 3:
                i3 = (Double) value;
                break;
            case 4:
                ia1 = (int[]) value;
                break;
            case 5:
                ia2 = (String[]) value;
                break;
            case 6:
                name = (byte[]) value;
            case 7:
                gl = (Glob) value;
            case 8:
                gla = (Glob[]) value;
            case 9:
                l1 = (long) value;
            case 10:
                la1 = (long[]) value;
            case 11:
                b = (boolean) value;
            default:
                throwError(field);
        }
        return this;
    }

    final public GlobType getType() {
        return GeneratedObjectGlobFactory.TYPE;
    }

    final public <T extends FieldValueVisitor> T accept(T functor) throws Exception {
        if ((isSet & (1 << 0)) != 0) {
            functor.visitInteger(GeneratedObjectGlobFactory.f1, i1);
        }
        if ((isSet & (1 << 1)) != 0) {
            functor.visitIntegerArray(GeneratedObjectGlobFactory.f2, ia1);
        }
        if ((isSet & (1 << 20)) != 0) {
            functor.visitIntegerArray(GeneratedObjectGlobFactory.f2, ia1);
        }
        return functor;
    }

    final public <T extends Functor>
    T apply(T functor) throws Exception {
        if (isSetAt(0)) {
            functor.process(GeneratedObjectGlobFactory.f1, i1);
        }
        if (isSetAt(1)) {
            functor.process(GeneratedObjectGlobFactory.f2, ia1);
        }
        if (isSetAt(20)) {
            functor.process(GeneratedObjectGlobFactory.f2, ia1);
        }
        return functor;
    }


    final public Object doGet(Field field) {
        final int index = field.getIndex();
        if (!isSetAt(index)) {
            return null;
        }
        return switch (index) {
            case 1 -> i1;
            case 2 -> i2;
            case 3 -> i3;
            case 4 -> ia1;
            case 5 -> ia2;
            case 6 -> name;
            case 7 -> gl;
            case 8 -> gla;
            case 9 -> l1;
            case 10 -> la1;
            case 11 -> b;
            default -> {
                throwError(field);
                yield null;
            }
        };
    }

//    static GlobGetAccessor getGetAccessor(Field field){
//        if (field.getIndex() == 0) {
//            return new GenGlobGetIntAccessor_i1();
//        }
//        else if (field.getIndex() == 1) {
//            return new GenGlobGetIntAccessor_ia1();
//        }
//    }
//
//    private static class GenGlobGetIntAccessor_i1 implements GlobGetIntAccessor {
//        public Object getValue(Glob glob) {
//            return get(glob);
//        }
//
//        public int get(Glob glob, int defaultValueIfNull) {
//            GeneratedObjectGlob generatedObjectGlob = (GeneratedObjectGlob) glob;
//            boolean isSetAt = generatedObjectGlob.isSetAt(0);
//            Integer val = generatedObjectGlob.i1;
//            return isSetAt ? val == null ? defaultValueIfNull : val : defaultValueIfNull;
//        }
//
//        public int getNative(Glob glob) {
//            GeneratedObjectGlob generatedObjectGlob = (GeneratedObjectGlob) glob;
//            boolean isSetAt = generatedObjectGlob.isSetAt(0);
//            Integer val = generatedObjectGlob.i1;
//            return isSetAt ? val == null ? 0 : val : 0;
//        }
//
//        public Integer get(Glob glob) {
//            GeneratedObjectGlob generatedObjectGlob = (GeneratedObjectGlob) glob;
//            return generatedObjectGlob.i1;
//        }
//    }
}
