package org.globsframework.model.generated.primitive;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.core.model.globaccessor.get.GlobGetIntAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.core.model.globaccessor.set.impl.AbstractGlobSetIntAccessor;
import org.globsframework.core.utils.exceptions.ItemNotFound;
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

    public static GlobGetAccessor getGetAccessor(Field field) {
        return switch (field.getIndex()) {
            case 0 -> PrimitiveGlobGetIntAccessor_i1.INSTANCE;
            default -> throw new IllegalStateException("Unexpected value: " + field.getIndex());
        };
    }

    public static GlobSetAccessor getSetAccessor(Field field) {
        return switch (field.getIndex()) {
            case 0 -> PrimitiveGlobSetIntAccessor_i1.INSTANCE;
            default -> throw new IllegalStateException("Unexpected value: " + field.getIndex());
        };
    }

    @Override
    public MutableGlob getMutable(GlobField field) throws ItemNotFound {
        return null;
    }

    @Override
    public MutableGlob[] getMutable(GlobArrayField field) throws ItemNotFound {
        return new MutableGlob[0];
    }

    @Override
    public MutableGlob getMutable(GlobUnionField field) throws ItemNotFound {
        return null;
    }

    @Override
    public MutableGlob[] getMutable(GlobArrayUnionField field) throws ItemNotFound {
        return new MutableGlob[0];
    }


    static class PrimitiveGlobSetIntAccessor_i1 extends AbstractGlobSetIntAccessor implements GlobSetIntAccessor {
        public static final GlobSetAccessor INSTANCE = new PrimitiveGlobSetIntAccessor_i1(GeneratedGlobPrimitiveFactory.f1);
        final int valueSet;
        final int valueUnSet;

        public PrimitiveGlobSetIntAccessor_i1(Field field) {
            valueSet = 1 << field.getIndex();
            valueUnSet = ~valueSet;
        }

        @Override
        public void set(MutableGlob glob, Integer value) {
            final GeneratedGlobPrimitiveGlob typeData = (GeneratedGlobPrimitiveGlob) glob;
            typeData.isSet |= valueSet;
            if (value == null) {
                typeData.isNull |= valueSet;
                typeData.i1 = 0;
            }
            else {
                typeData.isNull &= valueUnSet;
                typeData.i1 = value;
            }
        }

        @Override
        public void setNative(MutableGlob glob, int value) {
            final GeneratedGlobPrimitiveGlob typeData = (GeneratedGlobPrimitiveGlob) glob;
            typeData.isSet |= valueSet;
            typeData.isNull &= valueUnSet;
            typeData.i1 = value;
        }
    }

    static class PrimitiveGlobGetIntAccessor_i1 extends AbstractGlobGetNativeAccessor implements GlobGetIntAccessor {
        static final PrimitiveGlobGetIntAccessor_i1 INSTANCE = new PrimitiveGlobGetIntAccessor_i1(GeneratedGlobPrimitiveFactory.f1);
        private PrimitiveGlobGetIntAccessor_i1(Field field) {
            super(field);
        }

        @Override
        public int get(Glob glob, int defaultValueIfNull) {
            final GeneratedGlobPrimitiveGlob typeData = (GeneratedGlobPrimitiveGlob) glob;
            typeData.checkReserved();
            return uncheckedIsNull(typeData) ? defaultValueIfNull : typeData.i1;
        }

        public int getNative(Glob glob) {
            final GeneratedGlobPrimitiveGlob typeData = (GeneratedGlobPrimitiveGlob) glob;
            typeData.checkReserved();
            return typeData.i1;
        }

        @Override
        public Integer get(Glob glob) {
            final GeneratedGlobPrimitiveGlob typeData = (GeneratedGlobPrimitiveGlob) glob;
            typeData.checkReserved();
            if (uncheckedIsNull(typeData)) {
                return null;
            }
            return typeData.i1;
        }

        @Override
        public Object getValue(Glob glob) {
            return get(glob);
        }
    }



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
        if (isNull(index)) {// || !isSetAt(index)) {
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
