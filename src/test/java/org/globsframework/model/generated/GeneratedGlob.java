package org.globsframework.model.generated;

import org.globsframework.metamodel.fields.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.model.generator.AbstractGeneratedGlob32;

/*
org.objectweb.asm.util.ASMifier .../globs-generate/target/test-classes/org/globsframework/model/generated/GeneratedGlob.class
 */

public class GeneratedGlob extends AbstractGeneratedGlob32 {
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


    public MutableGlob doSet(Field field, Object value) {
//        throwError(field);
//        return this;
        final int index = field.getIndex();
        setSetAt(index);
        if (value == null) {
//            setNull(index);
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
        throwError(field);
//        setNull(index);
//        switch (index) {
//            case 1:
//                i1 = 0;
//                break;
//            case 2:
//                i2 = null;
//                break;
//            case 3:
//                i3 = 0.;
//                break;
//            case 4:
//                ia1 = null;
//                break;
//            case 5:
//                ia2 = null;
//                break;
//            case 6:
//                name = null;
//            case 7:
//                gl = null;
//            case 8:
//                gla = null;
//            case 9:
//                l1 = 0;
//            case 10:
//                la1 = null;
//            case 11:
//                b = false;
//            default:
//                throwError(field);
//        }
    }

    public GlobType getType() {
        return GeneratedGlobFactory.TYPE;
    }

    public Object doGet(Field field) {
//        throwError(field);
//        return null;
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