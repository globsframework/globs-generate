package org.globsframework.model.generated.primitive;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.model.generator.primitive.AbstractGeneratedGlob32;

/*
org.objectweb.asm.util.ASMifier .../globs-generate/target/test-classes/org/globsframework/model/generated/primitive/GeneratedGlobPrimitiveGlob.class
 */

public class GeneratedGlobPrimitiveGlob extends AbstractGeneratedGlob32 {
    private int i1;
    private int[] ia1;
    private String i2;
    private String[] ia2;
    private double i3;
    private byte[] name;
    private long l1;
    private long[] la1;
    private Glob gl;
    private Glob[] gla;
    private boolean b;


//    public <T extends FieldValues.Functor>
//    T apply(T functor) throws Exception {
//        if (isSetAt(0)) {
//            functor.process(GeneratedGlobFactory.f1, isNull(0) ? null : i1);
//        }
//        if (isSetAt(1)) {
//            functor.process(GeneratedGlobFactory.f2, isNull(0) ? null : ia2);
//        }
//        return functor;
//    }
//
//    public <T extends FieldValueVisitor> T accept(T functor) throws Exception {
//        for (Field field : getType().getFields()) {
//            if (isSet(field)) { //  || field.isKeyField()
//                field.accept(functor, doGet(field));
//            }
//        }
//        return functor;
//    }


    public MutableGlob doSet(Field field, Object value) {
        final int index = field.getIndex();
        setSetAt(index);
        if (value == null) {
            forceNull(field, index);
        } else {
            setNotNull(index);
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
        }
        return this;
    }

    private void forceNull(Field field, int index) {
        setNull(index);
        switch (index) {
            case 1:
                i1 = 0;
                break;
            case 2:
                i2 = null;
                break;
            case 3:
                i3 = 0.;
                break;
            case 4:
                ia1 = null;
                break;
            case 5:
                ia2 = null;
                break;
            case 6:
                name = null;
            case 7:
                gl = null;
            case 8:
                gla = null;
            case 9:
                l1 = 0;
            case 10:
                la1 = null;
            case 11:
                b = false;
            default:
                throwError(field);
        }
    }

    public GlobType getType() {
        return GeneratedGlobPrimitiveFactory.TYPE;
    }

    public Object doGet(Field field) {
        final int index = field.getIndex();
        if (isNull(index) || !isSetAt(index)) {
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


}
