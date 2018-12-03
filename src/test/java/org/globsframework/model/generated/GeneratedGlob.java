package org.globsframework.model.generated;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.fields.FieldValueVisitor;
import org.globsframework.model.Glob;
import org.globsframework.model.MutableGlob;
import org.globsframework.model.generator.AbstractGeneratedGlob;
import org.globsframework.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.model.globaccessor.get.impl.AbstractGlobGetDoubleAccessor;
import org.globsframework.model.globaccessor.get.impl.AbstractGlobGetIntArrayAccessor;
import org.globsframework.model.globaccessor.get.impl.AbstractGlobGetStringAccessor;
import org.globsframework.model.globaccessor.get.impl.AbstractGlobGetStringArrayAccessor;
import org.globsframework.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.model.globaccessor.set.impl.AbstractGlobSetIntArrayAccessor;
import org.globsframework.model.globaccessor.set.impl.AbstractGlobSetStringAccessor;
import org.globsframework.model.globaccessor.set.impl.AbstractGlobSetStringArrayAccessor;

public class GeneratedGlob extends AbstractGeneratedGlob {
    protected int i1;
    protected int[] ia1;
    protected String i2;
    protected String[] ia2;
    protected double i3;
    protected int nullFlag_1;

    public MutableGlob doSet(Field field, Object value) {
        if (value == null) {
            setNull(field);
        } else {
            switch (field.getIndex()) {
                case 1:
                    i1 = (Integer) value;
                    nullFlag_1 &= ~0x1;
                    break;
                case 2:
                    i2 = (String) value;
                    break;
                case 3:
                    i3 = (Double) value;
                    nullFlag_1 &= ~0x2;
                    break;
                case 4:
                    ia1 = (int[]) value;
                    break;
                case 5:
                    ia2 = (String[]) value;
                    break;
                default:
                    throwError(field);
            }
        }
        return this;
    }

    public GlobType getType() {
        return GeneratedGlobFactory.GLOB_TYPE;
    }

    public Object doGet(Field field) {
        switch (field.getIndex()) {
            case 1:
                return get_i1();
            case 2:
                return i2;
            case 3:
                return (nullFlag_1 & 0x2) == 0 ? i3 : null;
            case 4:
                return ia1;
            case 5:
                return ia2;
            default:
                throwError(field);
                return null;
        }
    }

    public Integer get_i1() {
        return (nullFlag_1 & 0x1) == 0 ? i1 : null;
    }

    public void setNative_i1(int value) {
        nullFlag_1 &= ~0x1;
        i1 = value;
    }

    public void set_i1(Integer value) {
        if (value == null) {
            nullFlag_1 |= 0x1;
        } else {
            nullFlag_1 &= ~0x1;
            i1 = value;
        }
    }

    public Double get_i3() {
        return (nullFlag_1 & 0x1) == 0 ? i3 : null;
    }

    public void setNative_i3(double value) {
        nullFlag_1 &= ~0x1;
        i3 = value;
    }

    public void set_i3(Double value) {
        if (value == null) {
            nullFlag_1 |= 0x1;
        } else {
            nullFlag_1 &= ~0x1;
            i3 = value;
        }
    }

    public int[] get_ia1() {
        return ia1;
    }

    public void set_ia1(int[] value) {
        ia1 = value;
    }


    public <T extends Functor> T apply(T functor) throws Exception {
        return GeneratedGlobFactory.processValue(this, functor);
    }

    public <T extends FieldValueVisitor> T accept(T functor) throws Exception {
        return GeneratedGlobFactory.acceptValueStatic(this, functor);
    }

    public Glob duplicate() {
        GeneratedGlob generatedGlob = new GeneratedGlob();
        generatedGlob.nullFlag_1 = nullFlag_1;
        generatedGlob.i1 = i1;
        generatedGlob.i2 = i2;
        generatedGlob.i3 = i3;
        return generatedGlob;
    }

    public static GlobSetAccessor getSetAccessor(Field field) {
        switch (field.getIndex()) {
            case 0:
                return GeneratedGlob.s1StringGlobDataSetter.INSTANCE;
            case 1:
                return GeneratedGlob.s1StringGlobDataSetter.INSTANCE;
            case 2:
                return GeneratedGlob.i1IntegerGlobDataSetter.INSTANCE;
            default:
                throwError(GeneratedGlobFactory.GLOB_TYPE, field);
                return null;
        }
    }

    public static GlobGetAccessor getGetAccessor(Field field) {
        switch (field.getIndex()) {
            case 0:
                return GeneratedGlob.s1GlobStringDataAccessor.INSTANCE;
            case 1:
                return GeneratedGlob.s1GlobStringDataAccessor.INSTANCE;
            case 2:
                return null;
            case 3:
                return null;
            case 4:
                return null;
            case 5:
                return null;
            case 6:
                return GeneratedGlob.d1GlobDoubleDataAccessor.INSTANCE;
            case 7:
                return null;
            case 9:
                return null;
            default:
                throwError(GeneratedGlobFactory.GLOB_TYPE, field);
                return null;
        }
    }

    private static class d1GlobDoubleDataAccessor extends AbstractGlobGetDoubleAccessor {
        public static final GeneratedGlob.d1GlobDoubleDataAccessor INSTANCE = new GeneratedGlob.d1GlobDoubleDataAccessor();

        public Double get(Glob glob) {
            return ((GeneratedGlob) glob).get_i3();
        }
    }

    private static class s1GlobStringDataAccessor extends AbstractGlobGetStringAccessor {
        public static final GeneratedGlob.s1GlobStringDataAccessor INSTANCE = new s1GlobStringDataAccessor();

        public String get(Glob glob) {
            return (((GeneratedGlob) glob).i2);
        }
    }

    private static class s1GlobStringArrayDataAccessor extends AbstractGlobGetStringArrayAccessor {
        public static final GeneratedGlob.s1GlobStringArrayDataAccessor INSTANCE = new s1GlobStringArrayDataAccessor();

        public String[] get(Glob glob) {
            return (((GeneratedGlob) glob).ia2);
        }
    }

    private static class s1GlobIntArrayDataAccessor extends AbstractGlobGetIntArrayAccessor {
        public static final GeneratedGlob.s1GlobIntArrayDataAccessor INSTANCE = new s1GlobIntArrayDataAccessor();

        public int[] get(Glob glob) {
            return (((GeneratedGlob) glob).ia1);
        }
    }

    private static final class s1StringGlobDataSetter extends AbstractGlobSetStringAccessor {
        public static final GeneratedGlob.s1StringGlobDataSetter INSTANCE = new GeneratedGlob.s1StringGlobDataSetter();

        public void set(MutableGlob glob, String value) {
            ((GeneratedGlob) glob).i2 = value;
        }
    }

    private static class i1IntegerGlobDataSetter implements GlobSetIntAccessor {
        public static final GeneratedGlob.i1IntegerGlobDataSetter INSTANCE = new GeneratedGlob.i1IntegerGlobDataSetter();

        public void set(MutableGlob glob, Integer value) {
            ((GeneratedGlob) glob).set_i1(value);
        }

        public void setNative(MutableGlob glob, int value) {
            ((GeneratedGlob) glob).setNative_i1(value);
        }

        public void setValue(MutableGlob glob, Object value) {
            ((GeneratedGlob) glob).set_i1((Integer) value);
        }
    }

    private static class i1IntegerArrayGlobDataSetter extends AbstractGlobSetIntArrayAccessor {
        public static final GeneratedGlob.i1IntegerArrayGlobDataSetter INSTANCE = new GeneratedGlob.i1IntegerArrayGlobDataSetter();

        public void set(MutableGlob glob, int[] value) {
            ((GeneratedGlob) glob).ia1 = value;
        }
    }

    private static class i1StringArrayGlobDataSetter extends AbstractGlobSetStringArrayAccessor {
        public static final GeneratedGlob.i1StringArrayGlobDataSetter INSTANCE = new GeneratedGlob.i1StringArrayGlobDataSetter();

        public void set(MutableGlob glob, String[] value) {
            ((GeneratedGlob) glob).ia2 = value;
        }
    }

    private void setNull(Field field) {
        switch (field.getIndex()) {
            case 0:
                nullFlag_1 |= (0x1);
                break;
            case 1:
                ia1 = null;
                break;
            case 2:
                ia1 = null;
                break;
            case 3:
                nullFlag_1 |= (0x2);
                break;
            default:
                throwError(field);
        }
    }

}