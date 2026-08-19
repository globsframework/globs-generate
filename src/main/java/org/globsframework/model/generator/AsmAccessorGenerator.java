package org.globsframework.model.generator;

import org.globsframework.core.metamodel.fields.*;
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
 * getCommonSuperClass is short-circuited because the generated Glob lives in {@link GeneratedClassLoader},
 * which is a child of the loader ASM itself runs in and therefore invisible to it — no frame merge in the
 * emitted methods needs a precise common supertype anyway.
 */
public class AsmAccessorGenerator {
    private static final String GET_BASE = "org/globsframework/model/generator/AbstractGeneratedGetAccessor";
    private static final String SET_BASE = "org/globsframework/model/generator/AbstractGeneratedSetAccessor";
    private static final String GLOB = "Lorg/globsframework/core/model/Glob;";
    private static final String MUTABLE_GLOB = "Lorg/globsframework/core/model/MutableGlob;";
    private static final String FIELD_DESC = "Lorg/globsframework/core/metamodel/fields/Field;";

    /**
     * The accessors of one generated type : each call loads the class {@link GeneratedClassLoader} was told
     * to emit for that field and direction. Called once per field per direction, from the factory's
     * constructor.
     * <p>
     * A failure here is a generation bug, not something a caller can act on, hence the unchecked wrapper :
     * the constructor of a GlobFactory cannot sensibly declare ReflectiveOperationException.
     */
    public static AccessorProvider providerFor(ClassLoader loader, String globInternalName) {
        return new AccessorProvider() {
            public GlobGetAccessor get(Field field) {
                return (GlobGetAccessor) newAccessor(getGetAccessorName(globInternalName, field.getIndex()), field);
            }

            public GlobSetAccessor set(Field field) {
                return (GlobSetAccessor) newAccessor(getSetAccessorName(globInternalName, field.getIndex()), field);
            }

            private Object newAccessor(String internalName, Field field) {
                try {
                    return loader.loadClass(internalName.replace('/', '.'))
                            .getDeclaredConstructor(Field.class)
                            .newInstance(field);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Can not build the generated accessor " + internalName
                                               + " for " + field.getName(), e);
                }
            }
        };
    }

    public static String getGetAccessorName(String globInternalName, int index) {
        return packageOf(globInternalName) + "Get_" + keyOf(globInternalName) + "_" + index;
    }

    public static String getSetAccessorName(String globInternalName, int index) {
        return packageOf(globInternalName) + "Set_" + keyOf(globInternalName) + "_" + index;
    }

    private static String packageOf(String internalName) {
        return internalName.substring(0, internalName.lastIndexOf('/') + 1);
    }

    /**
     * {@code Glob_DummyObject_ab12cd34ef56 -> DummyObject_ab12cd34ef56} : the family key, which is what
     * makes an accessor name unique across types — and stable across runs, since the key is.
     * <p>
     * Everything after the <em>first</em> underscore, i.e. the prefix removed rather than the last segment
     * kept : a key ends in a digest, but it ends in an ordinal instead when a family had to be built twice,
     * and two families whose keys differed only there would then get the same accessor names.
     */
    private static String keyOf(String internalName) {
        String simple = internalName.substring(internalName.lastIndexOf('/') + 1);
        return simple.substring(simple.indexOf('_') + 1);
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

    /**
     * Jumps to {@code target} when the {@code index}th bit of the {@code mask} field of the Glob in slot 1
     * matches : IFEQ for a clear bit, IFNE for a set one. Leaves the stack empty.
     */
    private static void jumpOnMaskBit(MethodVisitor methodVisitor, String globInternalName, String mask,
                                      int index, boolean is32Bit, int jumpOpcode, Label target) {
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitTypeInsn(CHECKCAST, globInternalName);
        if (is32Bit) {
            methodVisitor.visitFieldInsn(GETFIELD, globInternalName, mask, "I");
            methodVisitor.visitLdcInsn(1 << index);
            methodVisitor.visitInsn(IAND);
        } else {
            methodVisitor.visitFieldInsn(GETFIELD, globInternalName, mask, "J");
            methodVisitor.visitLdcInsn(1L << index);
            methodVisitor.visitInsn(LAND);
            methodVisitor.visitInsn(LCONST_0);
            methodVisitor.visitInsn(LCMP);
        }
        methodVisitor.visitJumpInsn(jumpOpcode, target);
    }

    private static void returnFalseThenTrue(MethodVisitor methodVisitor, Label trueLabel) {
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitLabel(trueLabel);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    /**
     * isSet and isNull, overriding the ones AbstractGeneratedGetAccessor answers through field.getIndex()
     * and — for isNull — through a boxing doGet. Here they are two mask reads on the Glob itself.
     */
    private static void generateIsSetAndIsNull(ClassWriter classWriter, String globInternalName, String fieldName,
                                               Field field, boolean primitive, boolean is32Bit, String fieldDesc) {
        int index = field.getIndex();

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "isSet", "(" + GLOB + ")Z", null, null);
        methodVisitor.visitCode();
        Label setLabel = new Label();
        jumpOnMaskBit(methodVisitor, globInternalName, "isSet", index, is32Bit, IFNE, setLabel);
        returnFalseThenTrue(methodVisitor, setLabel);

        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "isNull", "(" + GLOB + ")Z", null, null);
        methodVisitor.visitCode();
        Label nullLabel = new Label();
        if (primitive) {
            // same two tests as the generated get() : the null bit, then never-set
            jumpOnMaskBit(methodVisitor, globInternalName, "isNull", index, is32Bit, IFNE, nullLabel);
            jumpOnMaskBit(methodVisitor, globInternalName, "isSet", index, is32Bit, IFEQ, nullLabel);
        } else {
            // object flavour : the field holds the boxed value and unset() writes null into it, which is
            // the invariant the generated get() already relies on
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitTypeInsn(CHECKCAST, globInternalName);
            methodVisitor.visitFieldInsn(GETFIELD, globInternalName, fieldName, fieldDesc);
            methodVisitor.visitJumpInsn(IFNULL, nullLabel);
        }
        returnFalseThenTrue(methodVisitor, nullLabel);
    }

    public static byte[] generateGet(String globInternalName, String fieldName, Field field, boolean primitive,
                                     boolean is32Bit) {
        AccessorSpec spec = AccessorSpec.of(field);
        String name = getGetAccessorName(globInternalName, field.getIndex());
        String fieldDesc = primitive ? spec.nativeDesc : spec.valueDesc;

        ClassWriter classWriter = newClassWriter();
        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, name, null, GET_BASE, new String[]{spec.getInterface});
        generateConstructor(classWriter, GET_BASE);
        generateIsSetAndIsNull(classWriter, globInternalName, fieldName, field, primitive, is32Bit, fieldDesc);

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

        public void visitGlob(GlobField<?> field) {
            reference("Glob", "Lorg/globsframework/core/model/Glob;");
        }

        public void visitGlobArray(GlobArrayField<?> field) {
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
