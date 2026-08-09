package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.get.*;
import org.globsframework.core.model.globaccessor.set.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * The accessors used when a type asks not to have them generated (GeneratedOption / globs.generate.accessors) :
 * a typed object per field, on top of the Glob's doGet / doSet.
 * <p>
 * Slower than the generated ones -- doGet is a tableswitch on field.getIndex(), and it boxes on the primitive
 * flavour -- but they are plain classes: one per field kind for the whole JVM, where generation emits one per
 * field per type. At a call site shared by every type, such as a serializer looping over an array of per-field
 * writers, that difference flips the balance : ~20 receivers instead of hundreds is the difference between a
 * bimorphic and a hopelessly megamorphic site. Which one wins is workload-dependent, hence the option.
 */
public class DoGetAccessorProvider implements AccessorProvider {

    public GlobGetAccessor get(Field field) {
        AccessorBuilder builder = new AccessorBuilder();
        field.safeAccept(builder);
        return builder.getAccessor;
    }

    public GlobSetAccessor set(Field field) {
        AccessorBuilder builder = new AccessorBuilder();
        field.safeAccept(builder);
        return builder.setAccessor;
    }

    private static Object doGet(Glob glob, Field field) {
        return ((AbstractGlob) glob).doGet(field);
    }

    private static void doSet(MutableGlob glob, Field field, Object value) {
        ((AbstractMutableGlob) glob).doSet(field, value);
    }

    /** isSet / isNull are shared by every get accessor : only the typed get() differs. */
    private abstract static class AbstractGet implements GlobGetAccessor {
        final Field field;

        AbstractGet(Field field) {
            this.field = field;
        }

        public boolean isSet(Glob glob) {
            return glob.isSet(field);
        }

        public boolean isNull(Glob glob) {
            return doGet(glob, field) == null;
        }
    }

    private abstract static class AbstractSet {
        final Field field;

        AbstractSet(Field field) {
            this.field = field;
        }
    }

    private static class GetInteger extends AbstractGet implements GlobGetIntAccessor {
        GetInteger(Field field) { super(field); }
        public Integer get(Glob glob) { return (Integer) doGet(glob, field); }
    }

    private static class GetIntegerArray extends AbstractGet implements GlobGetIntArrayAccessor {
        GetIntegerArray(Field field) { super(field); }
        public int[] get(Glob glob) { return (int[]) doGet(glob, field); }
    }

    private static class GetDouble extends AbstractGet implements GlobGetDoubleAccessor {
        GetDouble(Field field) { super(field); }
        public Double get(Glob glob) { return (Double) doGet(glob, field); }
    }

    private static class GetDoubleArray extends AbstractGet implements GlobGetDoubleArrayAccessor {
        GetDoubleArray(Field field) { super(field); }
        public double[] get(Glob glob) { return (double[]) doGet(glob, field); }
    }

    private static class GetLong extends AbstractGet implements GlobGetLongAccessor {
        GetLong(Field field) { super(field); }
        public Long get(Glob glob) { return (Long) doGet(glob, field); }
    }

    private static class GetLongArray extends AbstractGet implements GlobGetLongArrayAccessor {
        GetLongArray(Field field) { super(field); }
        public long[] get(Glob glob) { return (long[]) doGet(glob, field); }
    }

    private static class GetBigDecimal extends AbstractGet implements GlobGetBigDecimalAccessor {
        GetBigDecimal(Field field) { super(field); }
        public BigDecimal get(Glob glob) { return (BigDecimal) doGet(glob, field); }
    }

    private static class GetBigDecimalArray extends AbstractGet implements GlobGetBigDecimalArrayAccessor {
        GetBigDecimalArray(Field field) { super(field); }
        public BigDecimal[] get(Glob glob) { return (BigDecimal[]) doGet(glob, field); }
    }

    private static class GetString extends AbstractGet implements GlobGetStringAccessor {
        GetString(Field field) { super(field); }
        public String get(Glob glob) { return (String) doGet(glob, field); }
    }

    private static class GetStringArray extends AbstractGet implements GlobGetStringArrayAccessor {
        GetStringArray(Field field) { super(field); }
        public String[] get(Glob glob) { return (String[]) doGet(glob, field); }
    }

    private static class GetBoolean extends AbstractGet implements GlobGetBooleanAccessor {
        GetBoolean(Field field) { super(field); }
        public Boolean get(Glob glob) { return (Boolean) doGet(glob, field); }
    }

    private static class GetBooleanArray extends AbstractGet implements GlobGetBooleanArrayAccessor {
        GetBooleanArray(Field field) { super(field); }
        public boolean[] get(Glob glob) { return (boolean[]) doGet(glob, field); }
    }

    private static class GetDate extends AbstractGet implements GlobGetDateAccessor {
        GetDate(Field field) { super(field); }
        public LocalDate get(Glob glob) { return (LocalDate) doGet(glob, field); }
    }

    private static class GetDateTime extends AbstractGet implements GlobGetDateTimeAccessor {
        GetDateTime(Field field) { super(field); }
        public ZonedDateTime get(Glob glob) { return (ZonedDateTime) doGet(glob, field); }
    }

    private static class GetBytes extends AbstractGet implements GlobGetBytesAccessor {
        GetBytes(Field field) { super(field); }
        public byte[] get(Glob glob) { return (byte[]) doGet(glob, field); }
    }

    private static class GetGlob extends AbstractGet implements GlobGetGlobAccessor {
        GetGlob(Field field) { super(field); }
        public Glob get(Glob glob) { return (Glob) doGet(glob, field); }
    }

    private static class GetGlobArray extends AbstractGet implements GlobGetGlobArrayAccessor {
        GetGlobArray(Field field) { super(field); }
        public Glob[] get(Glob glob) { return (Glob[]) doGet(glob, field); }
    }

    private static class SetInteger extends AbstractSet implements GlobSetIntAccessor {
        SetInteger(Field field) { super(field); }
        public void set(MutableGlob glob, Integer value) { doSet(glob, field, value); }
    }

    private static class SetIntegerArray extends AbstractSet implements GlobSetIntArrayAccessor {
        SetIntegerArray(Field field) { super(field); }
        public void set(MutableGlob glob, int[] value) { doSet(glob, field, value); }
    }

    private static class SetDouble extends AbstractSet implements GlobSetDoubleAccessor {
        SetDouble(Field field) { super(field); }
        public void set(MutableGlob glob, Double value) { doSet(glob, field, value); }
    }

    private static class SetDoubleArray extends AbstractSet implements GlobSetDoubleArrayAccessor {
        SetDoubleArray(Field field) { super(field); }
        public void set(MutableGlob glob, double[] value) { doSet(glob, field, value); }
    }

    private static class SetLong extends AbstractSet implements GlobSetLongAccessor {
        SetLong(Field field) { super(field); }
        public void set(MutableGlob glob, Long value) { doSet(glob, field, value); }
    }

    private static class SetLongArray extends AbstractSet implements GlobSetLongArrayAccessor {
        SetLongArray(Field field) { super(field); }
        public void set(MutableGlob glob, long[] value) { doSet(glob, field, value); }
    }

    private static class SetBigDecimal extends AbstractSet implements GlobSetBigDecimalAccessor {
        SetBigDecimal(Field field) { super(field); }
        public void set(MutableGlob glob, BigDecimal value) { doSet(glob, field, value); }
    }

    private static class SetBigDecimalArray extends AbstractSet implements GlobSetBigDecimalArrayAccessor {
        SetBigDecimalArray(Field field) { super(field); }
        public void set(MutableGlob glob, BigDecimal[] value) { doSet(glob, field, value); }
    }

    private static class SetString extends AbstractSet implements GlobSetStringAccessor {
        SetString(Field field) { super(field); }
        public void set(MutableGlob glob, String value) { doSet(glob, field, value); }
    }

    private static class SetStringArray extends AbstractSet implements GlobSetStringArrayAccessor {
        SetStringArray(Field field) { super(field); }
        public void set(MutableGlob glob, String[] value) { doSet(glob, field, value); }
    }

    private static class SetBoolean extends AbstractSet implements GlobSetBooleanAccessor {
        SetBoolean(Field field) { super(field); }
        public void set(MutableGlob glob, Boolean value) { doSet(glob, field, value); }
    }

    private static class SetBooleanArray extends AbstractSet implements GlobSetBooleanArrayAccessor {
        SetBooleanArray(Field field) { super(field); }
        public void set(MutableGlob glob, boolean[] value) { doSet(glob, field, value); }
    }

    private static class SetDate extends AbstractSet implements GlobSetDateAccessor {
        SetDate(Field field) { super(field); }
        public void set(MutableGlob glob, LocalDate value) { doSet(glob, field, value); }
    }

    private static class SetDateTime extends AbstractSet implements GlobSetDateTimeAccessor {
        SetDateTime(Field field) { super(field); }
        public void set(MutableGlob glob, ZonedDateTime value) { doSet(glob, field, value); }
    }

    private static class SetBytes extends AbstractSet implements GlobSetBytesAccessor {
        SetBytes(Field field) { super(field); }
        public void set(MutableGlob glob, byte[] value) { doSet(glob, field, value); }
    }

    private static class SetGlob extends AbstractSet implements GlobSetGlobAccessor {
        SetGlob(Field field) { super(field); }
        public void set(MutableGlob glob, Glob value) { doSet(glob, field, value); }
    }

    private static class SetGlobArray extends AbstractSet implements GlobSetGlobArrayAccessor {
        SetGlobArray(Field field) { super(field); }
        public void set(MutableGlob glob, Glob[] values) { doSet(glob, field, values); }
    }

    /** Union fields carry a Glob (resp. a Glob[]) so they reuse the Glob accessors. */
    private static class AccessorBuilder implements FieldVisitor {
        GlobGetAccessor getAccessor;
        GlobSetAccessor setAccessor;

        public void visitInteger(IntegerField field) {
            getAccessor = new GetInteger(field);
            setAccessor = new SetInteger(field);
        }

        public void visitIntegerArray(IntegerArrayField field) {
            getAccessor = new GetIntegerArray(field);
            setAccessor = new SetIntegerArray(field);
        }

        public void visitDouble(DoubleField field) {
            getAccessor = new GetDouble(field);
            setAccessor = new SetDouble(field);
        }

        public void visitDoubleArray(DoubleArrayField field) {
            getAccessor = new GetDoubleArray(field);
            setAccessor = new SetDoubleArray(field);
        }

        public void visitLong(LongField field) {
            getAccessor = new GetLong(field);
            setAccessor = new SetLong(field);
        }

        public void visitLongArray(LongArrayField field) {
            getAccessor = new GetLongArray(field);
            setAccessor = new SetLongArray(field);
        }

        public void visitBigDecimal(BigDecimalField field) {
            getAccessor = new GetBigDecimal(field);
            setAccessor = new SetBigDecimal(field);
        }

        public void visitBigDecimalArray(BigDecimalArrayField field) {
            getAccessor = new GetBigDecimalArray(field);
            setAccessor = new SetBigDecimalArray(field);
        }

        public void visitString(StringField field) {
            getAccessor = new GetString(field);
            setAccessor = new SetString(field);
        }

        public void visitStringArray(StringArrayField field) {
            getAccessor = new GetStringArray(field);
            setAccessor = new SetStringArray(field);
        }

        public void visitBoolean(BooleanField field) {
            getAccessor = new GetBoolean(field);
            setAccessor = new SetBoolean(field);
        }

        public void visitBooleanArray(BooleanArrayField field) {
            getAccessor = new GetBooleanArray(field);
            setAccessor = new SetBooleanArray(field);
        }

        public void visitDate(DateField field) {
            getAccessor = new GetDate(field);
            setAccessor = new SetDate(field);
        }

        public void visitDateTime(DateTimeField field) {
            getAccessor = new GetDateTime(field);
            setAccessor = new SetDateTime(field);
        }

        public void visitBytes(BytesField field) {
            getAccessor = new GetBytes(field);
            setAccessor = new SetBytes(field);
        }

        public void visitGlob(GlobField<?> field) {
            getAccessor = new GetGlob(field);
            setAccessor = new SetGlob(field);
        }

        public void visitGlobArray(GlobArrayField<?> field) {
            getAccessor = new GetGlobArray(field);
            setAccessor = new SetGlobArray(field);
        }

        public void visitUnionGlob(GlobUnionField field) {
            getAccessor = new GetGlob(field);
            setAccessor = new SetGlob(field);
        }

        public void visitUnionGlobArray(GlobArrayUnionField field) {
            getAccessor = new GetGlobArray(field);
            setAccessor = new SetGlobArray(field);
        }
    }
}
