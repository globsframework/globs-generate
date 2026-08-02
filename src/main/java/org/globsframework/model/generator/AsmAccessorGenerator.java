package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.GlobFactory;
import org.globsframework.core.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/**
 * Generates one accessor class per field and per direction, reading and writing the Glob field directly
 * instead of going through the doGet / doSet tableswitch.
 * <p>
 * Unlike the Glob and factory generators, these classes are written with COMPUTE_FRAMES : the null handling
 * of the primitive flavour is branchy, and hand-computing those frames buys nothing but VerifyErrors.
 * getCommonSuperClass is short-circuited because the generated Glob lives in a throwaway ClassLoader that
 * ASM cannot resolve — no frame merge in the emitted methods needs a precise common supertype.
 */
public class AsmAccessorGenerator {
    /**
     * Experiment switch : when false the factory keeps the doGet/doSet-based accessors built by
     * AbstractGeneratedGlobFactory's constructor. Read at generation time, like UNROLL_VISITORS.
     */
    public static boolean GENERATE_ACCESSORS = Boolean.parseBoolean(
            System.getProperty("globs.generate.accessors", "true"));

    private static final String GET_BASE = "org/globsframework/model/generator/AbstractGeneratedGetAccessor";
    private static final String SET_BASE = "org/globsframework/model/generator/AbstractGeneratedSetAccessor";
    private static final String GLOB = "Lorg/globsframework/core/model/Glob;";
    private static final String MUTABLE_GLOB = "Lorg/globsframework/core/model/MutableGlob;";
    private static final String FIELD_DESC = "Lorg/globsframework/core/metamodel/fields/Field;";

    /**
     * Loads the accessor classes generated for this type and hands them to the factory, replacing the
     * doGet/doSet-based ones its constructor installed.
     */
    public static void installAccessors(GlobFactory factory, GlobType globType, ClassLoader loader,
                                        String globInternalName) throws ReflectiveOperationException {
        if (!GENERATE_ACCESSORS) {
            return;
        }
        AbstractGeneratedGlobFactory generated = (AbstractGeneratedGlobFactory) factory;
        for (Field field : globType.getFields()) {
            Object get = newAccessor(loader, getGetAccessorName(globInternalName, field.getIndex()), field);
            Object set = newAccessor(loader, getSetAccessorName(globInternalName, field.getIndex()), field);
            generated.installAccessors(field, (GlobGetAccessor) get, (GlobSetAccessor) set);
        }
    }

    private static Object newAccessor(ClassLoader loader, String internalName, Field field)
            throws ReflectiveOperationException {
        return loader.loadClass(internalName.replace('/', '.'))
                .getDeclaredConstructor(Field.class)
                .newInstance(field);
    }

    public static String getGetAccessorName(String globInternalName, int index) {
        return packageOf(globInternalName) + "GeneratedGetAccessor_" + suffixOf(globInternalName) + "_" + index;
    }

    public static String getSetAccessorName(String globInternalName, int index) {
        return packageOf(globInternalName) + "GeneratedSetAccessor_" + suffixOf(globInternalName) + "_" + index;
    }

    private static String packageOf(String internalName) {
        return internalName.substring(0, internalName.lastIndexOf('/') + 1);
    }

    /** GeneratedGlob_7 -> 7, so accessor names stay unique across types. */
    private static String suffixOf(String internalName) {
        String simple = internalName.substring(internalName.lastIndexOf('/') + 1);
        return simple.substring(simple.lastIndexOf('_') + 1);
    }

    private static ClassWriter newClassWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
    }

    private static void generateConstructor(ClassWriter classWriter, String base) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "(" + FIELD_DESC + ")V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, base, "<init>", "(" + FIELD_DESC + ")V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static void pushIndex(MethodVisitor methodVisitor, int index) {
        if (index <= Byte.MAX_VALUE) {
            methodVisitor.visitIntInsn(BIPUSH, index);
        } else {
            methodVisitor.visitIntInsn(SIPUSH, index);
        }
    }

    public static byte[] generateGet(String globInternalName, String fieldName, Field field, boolean primitive) {
        AccessorSpec spec = AccessorSpec.of(field);
        String name = getGetAccessorName(globInternalName, field.getIndex());
        String fieldDesc = primitive ? spec.nativeDesc : spec.valueDesc;

        ClassWriter classWriter = newClassWriter();
        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, name, null, GET_BASE, new String[]{spec.getInterface});
        generateConstructor(classWriter, GET_BASE);

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "get", "(" + GLOB + ")" + spec.valueDesc, null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitTypeInsn(CHECKCAST, globInternalName);
        if (primitive) {
            // mirror doGet exactly : null when the null bit is on or the field was never set
            methodVisitor.visitVarInsn(ASTORE, 2);
            Label nullLabel = new Label();
            methodVisitor.visitVarInsn(ALOAD, 2);
            pushIndex(methodVisitor, field.getIndex());
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, globInternalName, "isNull", "(I)Z", false);
            methodVisitor.visitJumpInsn(IFNE, nullLabel);
            methodVisitor.visitVarInsn(ALOAD, 2);
            pushIndex(methodVisitor, field.getIndex());
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, globInternalName, "isSetAt", "(I)Z", false);
            methodVisitor.visitJumpInsn(IFEQ, nullLabel);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitFieldInsn(GETFIELD, globInternalName, fieldName, fieldDesc);
            if (spec.boxedOwner != null) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, spec.boxedOwner, "valueOf",
                        "(" + spec.nativeDesc + ")" + spec.valueDesc, false);
            }
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitLabel(nullLabel);
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitInsn(ARETURN);
        } else {
            // object flavour : the field itself is the boxed value, and unset() writes null into it
            methodVisitor.visitFieldInsn(GETFIELD, globInternalName, fieldName, fieldDesc);
            methodVisitor.visitInsn(ARETURN);
        }
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        // skipping the boxing entirely is the whole point of the primitive flavour
        if (primitive && spec.boxedOwner != null) {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getNative", "(" + GLOB + ")" + spec.nativeDesc, null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitTypeInsn(CHECKCAST, globInternalName);
            methodVisitor.visitFieldInsn(GETFIELD, globInternalName, fieldName, fieldDesc);
            methodVisitor.visitInsn(spec.returnOpcode);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    public static byte[] generateSet(String globInternalName, String fieldName, Field field, boolean primitive) {
        AccessorSpec spec = AccessorSpec.of(field);
        String name = getSetAccessorName(globInternalName, field.getIndex());
        String fieldDesc = primitive ? spec.nativeDesc : spec.valueDesc;
        int index = field.getIndex();

        ClassWriter classWriter = newClassWriter();
        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, name, null, SET_BASE, new String[]{spec.setInterface});
        generateConstructor(classWriter, SET_BASE);

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "set",
                "(" + MUTABLE_GLOB + spec.valueDesc + ")V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitTypeInsn(CHECKCAST, globInternalName);
        methodVisitor.visitVarInsn(ASTORE, 3);
        methodVisitor.visitVarInsn(ALOAD, 3);
        pushIndex(methodVisitor, index);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, globInternalName, "setSetAt", "(I)V", false);
        if (primitive) {
            Label nullLabel = new Label();
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitJumpInsn(IFNULL, nullLabel);
            methodVisitor.visitVarInsn(ALOAD, 3);
            pushIndex(methodVisitor, index);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, globInternalName, "setNotNull", "(I)V", false);
            methodVisitor.visitVarInsn(ALOAD, 3);
            methodVisitor.visitVarInsn(ALOAD, 2);
            if (spec.boxedOwner != null) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, spec.boxedOwner, spec.unboxMethod,
                        "()" + spec.nativeDesc, false);
            }
            methodVisitor.visitFieldInsn(PUTFIELD, globInternalName, fieldName, fieldDesc);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitLabel(nullLabel);
            methodVisitor.visitVarInsn(ALOAD, 3);
            pushIndex(methodVisitor, index);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, globInternalName, "setNull", "(I)V", false);
            methodVisitor.visitVarInsn(ALOAD, 3);
            methodVisitor.visitInsn(spec.zeroOpcode);
            methodVisitor.visitFieldInsn(PUTFIELD, globInternalName, fieldName, fieldDesc);
            methodVisitor.visitInsn(RETURN);
        } else {
            methodVisitor.visitVarInsn(ALOAD, 3);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitFieldInsn(PUTFIELD, globInternalName, fieldName, fieldDesc);
            methodVisitor.visitInsn(RETURN);
        }
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        if (primitive && spec.boxedOwner != null) {
            // a long / double argument takes slots 2 AND 3, so the cast Glob has to go after it
            int globSlot = "J".equals(spec.nativeDesc) || "D".equals(spec.nativeDesc) ? 4 : 3;
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "setNative",
                    "(" + MUTABLE_GLOB + spec.nativeDesc + ")V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitTypeInsn(CHECKCAST, globInternalName);
            methodVisitor.visitVarInsn(ASTORE, globSlot);
            methodVisitor.visitVarInsn(ALOAD, globSlot);
            pushIndex(methodVisitor, index);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, globInternalName, "setSetAt", "(I)V", false);
            methodVisitor.visitVarInsn(ALOAD, globSlot);
            pushIndex(methodVisitor, index);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, globInternalName, "setNotNull", "(I)V", false);
            methodVisitor.visitVarInsn(ALOAD, globSlot);
            methodVisitor.visitVarInsn(spec.loadOpcode, 2);
            methodVisitor.visitFieldInsn(PUTFIELD, globInternalName, fieldName, fieldDesc);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    /**
     * Everything the two generators need to know about one kind of field : the accessor interfaces to
     * implement, the descriptor seen by callers (always the boxed / reference type) and, when the primitive
     * flavour stores it natively, how to box, unbox and zero it.
     */
    static class AccessorSpec implements FieldVisitor {
        private static final String GET = "org/globsframework/core/model/globaccessor/get/GlobGet";
        private static final String SET = "org/globsframework/core/model/globaccessor/set/GlobSet";

        String getInterface;
        String setInterface;
        String valueDesc;
        String nativeDesc;
        String boxedOwner;
        String unboxMethod;
        int zeroOpcode = ACONST_NULL;
        int returnOpcode = ARETURN;
        int loadOpcode = ALOAD;

        static AccessorSpec of(Field field) {
            AccessorSpec spec = new AccessorSpec();
            field.safeAccept(spec);
            return spec;
        }

        private void reference(String kind, String desc) {
            getInterface = GET + kind + "Accessor";
            setInterface = SET + kind + "Accessor";
            valueDesc = desc;
            nativeDesc = desc;
        }

        private void nativeKind(String kind, String desc, String boxed, String unbox,
                                int zero, int ret, int load) {
            getInterface = GET + kind + "Accessor";
            setInterface = SET + kind + "Accessor";
            valueDesc = "L" + boxed + ";";
            nativeDesc = desc;
            boxedOwner = boxed;
            unboxMethod = unbox;
            zeroOpcode = zero;
            returnOpcode = ret;
            loadOpcode = load;
        }

        public void visitInteger(IntegerField field) {
            nativeKind("Int", "I", "java/lang/Integer", "intValue", ICONST_0, IRETURN, ILOAD);
        }

        public void visitDouble(DoubleField field) {
            nativeKind("Double", "D", "java/lang/Double", "doubleValue", DCONST_0, DRETURN, DLOAD);
        }

        public void visitLong(LongField field) {
            nativeKind("Long", "J", "java/lang/Long", "longValue", LCONST_0, LRETURN, LLOAD);
        }

        public void visitBoolean(BooleanField field) {
            nativeKind("Boolean", "Z", "java/lang/Boolean", "booleanValue", ICONST_0, IRETURN, ILOAD);
        }

        public void visitIntegerArray(IntegerArrayField field) {
            reference("IntArray", "[I");
        }

        public void visitDoubleArray(DoubleArrayField field) {
            reference("DoubleArray", "[D");
        }

        public void visitLongArray(LongArrayField field) {
            reference("LongArray", "[J");
        }

        public void visitBooleanArray(BooleanArrayField field) {
            reference("BooleanArray", "[Z");
        }

        public void visitString(StringField field) {
            reference("String", "Ljava/lang/String;");
        }

        public void visitStringArray(StringArrayField field) {
            reference("StringArray", "[Ljava/lang/String;");
        }

        public void visitBigDecimal(BigDecimalField field) {
            reference("BigDecimal", "Ljava/math/BigDecimal;");
        }

        public void visitBigDecimalArray(BigDecimalArrayField field) {
            reference("BigDecimalArray", "[Ljava/math/BigDecimal;");
        }

        public void visitDate(DateField field) {
            reference("Date", "Ljava/time/LocalDate;");
        }

        public void visitDateTime(DateTimeField field) {
            reference("DateTime", "Ljava/time/ZonedDateTime;");
        }

        public void visitBytes(BytesField field) {
            reference("Bytes", "[B");
        }

        public void visitGlob(GlobField field) {
            reference("Glob", "Lorg/globsframework/core/model/Glob;");
        }

        public void visitGlobArray(GlobArrayField field) {
            reference("GlobArray", "[Lorg/globsframework/core/model/Glob;");
        }

        public void visitUnionGlob(GlobUnionField field) {
            reference("Glob", "Lorg/globsframework/core/model/Glob;");
        }

        public void visitUnionGlobArray(GlobArrayUnionField field) {
            reference("GlobArray", "[Lorg/globsframework/core/model/Glob;");
        }
    }
}
