package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.generate.FieldValueFunction;
import org.globsframework.core.model.generate.GenerateCaller;
import org.globsframework.core.model.generate.GeneratedFunctionCaller;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.objectweb.asm.Opcodes.*;

/**
 * Generates the {@link GeneratedFunctionCaller} of one GlobType : a class holding one
 * {@code public static final FieldValueFunction} per field, and a {@code call} unrolled over them.
 * <p>
 * The point is not to save the loop — it is to give the JVM one call site per field instead of one for all
 * of them. A {@code static final} read is a constant to the JIT, so each {@code INVOKEINTERFACE call} sees a
 * single receiver type and inlines, where the loop of a hand-written serializer sees every function of every
 * field of every type and stays megamorphic. That is also why a class is emitted per {@link #create} rather
 * than per type : two callers over the same type hold different functions, and sharing the class would put
 * them back on the same call sites.
 * <p>
 * Like {@link AsmAccessorGenerator} this reads the value fields and the masks of the generated Glob straight
 * out of another package (they are {@code public} for exactly that reason), and for the same reason it is
 * written with COMPUTE_FRAMES : the null handling is branchy and hand-computed frames would buy nothing but
 * VerifyErrors.
 * <p>
 * Boxing : the interface is generic, so a primitive-flavour value has to be boxed to be passed. Since the
 * call site is monomorphic and small, the box normally dies in escape analysis once the function is inlined —
 * but it is a real allocation whenever it is not.
 */
public class AsmCallerGenerator {
    private static final String GENERATOR = "org/globsframework/model/generator/AsmCallerGenerator";
    private static final String FUNCTION = "org/globsframework/core/model/generate/FieldValueFunction";
    private static final String FUNCTION_DESC = "L" + FUNCTION + ";";
    private static final String FUNCTIONS_DESC = "[" + FUNCTION_DESC;
    private static final String CALLER = "org/globsframework/core/model/generate/GeneratedFunctionCaller";
    private static final String GLOB = "Lorg/globsframework/core/model/Glob;";
    private static final String OBJECT = "Ljava/lang/Object;";

    // the Glob is cast once into slot 4; 5 and 6 are the per-field scratch (the null flag / the value)
    private static final int GLOB_SLOT = 4;
    private static final int FLAG_SLOT = 5;
    private static final int VALUE_SLOT = 6;

    private static final AtomicInteger ID = new AtomicInteger();
    // What the generated caller's <clinit> reads. Same protocol as the PENDING map of the two generators :
    // the entry lives only for the duration of create, so nothing here keeps a function -- nor the
    // ClassLoader of the generated class -- alive.
    private static final Map<Integer, FieldValueFunction[]> PENDING = new ConcurrentHashMap<>();

    /**
     * The GenerateCaller handed to the factory of a generated type. It closes over the ClassLoader holding
     * the generated Glob class, which is the only thing that can resolve it — that is why the factory cannot
     * build this on its own and gets it from the generator, like its AccessorProvider.
     *
     * @param globInternalName the generated Glob class, whose public fields and masks the caller reads
     * @param primitive        which flavour that Glob is : native fields plus an isNull mask, or boxed ones
     */
    public static GenerateCaller generatorFor(ClassLoader globLoader, String globInternalName, GlobType type,
                                              boolean primitive) {
        return new GenerateCaller() {
            public <D, E> GeneratedFunctionCaller<D, E> create(GetFieldValueFunction<D, E> getFieldValueFunction) {
                return generate(globLoader, globInternalName, type, primitive, getFieldValueFunction);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <D, E> GeneratedFunctionCaller<D, E> generate(ClassLoader globLoader, String globInternalName,
                                                                 GlobType type, boolean primitive,
                                                                 GenerateCaller.GetFieldValueFunction<D, E> provider) {
        Field[] fields = type.getFields();
        FieldValueFunction[] functions = new FieldValueFunction[fields.length];
        for (Field field : fields) {
            FieldValueFunction<?, D, E> function = provider.create(field);
            if (function == null) {
                throw new IllegalArgumentException("No FieldValueFunction for " + field.getName()
                                                   + " of " + type.getName());
            }
            functions[field.getIndex()] = function;
        }

        int id = ID.incrementAndGet();
        String callerName = getCallerName(globInternalName, id);
        // a child of the loader holding the Glob class : it sees it through delegation, and it is thrown
        // away with the caller it was built for
        ClassLoader loader = new ClassLoader(globLoader) {
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.replace('.', '/').equals(callerName)) {
                    byte[] b = generateCaller(callerName, globInternalName, type, primitive, id);
                    return super.defineClass(name, b, 0, b.length);
                }
                return super.findClass(name);
            }
        };

        PENDING.put(id, functions);
        try {
            // newInstance triggers the <clinit> that reads PENDING
            return (GeneratedFunctionCaller<D, E>) loader.loadClass(callerName.replace('/', '.'))
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Can not generate the caller of " + type.getName() + " : " + e.getMessage(), e);
        } finally {
            PENDING.remove(id);
        }
    }

    /** Called from the generated caller's {@code <clinit>}, which runs while generate is still on the stack. */
    public static FieldValueFunction[] getFunctions(int id) {
        FieldValueFunction[] functions = PENDING.get(id);
        if (functions == null) {
            throw new IllegalStateException("Nothing registered for generated caller " + id
                                            + " : the generated class was initialized outside of create.");
        }
        return functions;
    }

    static String getCallerName(String globInternalName, int id) {
        String simple = globInternalName.substring(globInternalName.lastIndexOf('/') + 1);
        return globInternalName.substring(0, globInternalName.lastIndexOf('/') + 1)
               + "GeneratedFunctionCaller_" + simple.substring(simple.lastIndexOf('_') + 1) + "_" + id;
    }

    private static String functionName(Field field) {
        return "fn_" + field.getIndex();
    }

    static byte[] generateCaller(String callerName, String globInternalName, GlobType type, boolean primitive,
                                 int id) {
        Field[] fields = type.getFields();
        boolean is32Bit = type.getFieldCount() <= 32;

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            // the generated Glob lives in a ClassLoader ASM cannot resolve, and no frame merge here needs a
            // precise common supertype
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        classWriter.visit(V17, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, callerName, null, "java/lang/Object",
                new String[]{CALLER});

        for (Field field : fields) {
            classWriter.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, functionName(field), FUNCTION_DESC,
                    null, null).visitEnd();
        }

        {
            MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }
        {
            MethodVisitor methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitLdcInsn(id);
            methodVisitor.visitMethodInsn(INVOKESTATIC, GENERATOR, "getFunctions", "(I)" + FUNCTIONS_DESC, false);
            methodVisitor.visitVarInsn(ASTORE, 0);
            for (Field field : fields) {
                methodVisitor.visitVarInsn(ALOAD, 0);
                pushInt(methodVisitor, field.getIndex());
                methodVisitor.visitInsn(AALOAD);
                methodVisitor.visitFieldInsn(PUTSTATIC, callerName, functionName(field), FUNCTION_DESC);
            }
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }
        {
            MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "call",
                    "(" + GLOB + OBJECT + OBJECT + ")V", null, null);
            methodVisitor.visitCode();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitTypeInsn(CHECKCAST, globInternalName);
                methodVisitor.visitVarInsn(ASTORE, GLOB_SLOT);
                for (Field field : fields) {
                    emitFieldCall(methodVisitor, callerName, globInternalName, field, primitive, is32Bit);
                }
            }
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    /**
     * {@code fn_i.call(isSet, isNull, value, ctx1, ctx2)} for one field.
     * <p>
     * isSet and isNull say the same thing as the Glob does : isSet is the mask bit, and isNull is what doGet
     * answers null for — the null bit or a field that was never set on the primitive flavour, the value being
     * null on the object one (unset() writes null into it, which the generated doGet already relies on).
     */
    private static void emitFieldCall(MethodVisitor methodVisitor, String callerName, String globInternalName,
                                      Field field, boolean primitive, boolean is32Bit) {
        AsmAccessorGenerator.AccessorSpec spec = AsmAccessorGenerator.AccessorSpec.of(field);
        String fieldName = AsmFactoryGenerator.fieldName(field);
        String fieldDesc = primitive ? spec.nativeDesc : spec.valueDesc;
        int index = field.getIndex();

        if (primitive) {
            // isNull = ((isNull | ~isSet) >>> index) & 1, branchless, and needed twice : as the argument and
            // as the test guarding the boxing
            pushMask(methodVisitor, globInternalName, "isNull", is32Bit);
            pushMask(methodVisitor, globInternalName, "isSet", is32Bit);
            if (is32Bit) {
                methodVisitor.visitInsn(ICONST_M1);
                methodVisitor.visitInsn(IXOR);
                methodVisitor.visitInsn(IOR);
            } else {
                methodVisitor.visitLdcInsn(-1L);
                methodVisitor.visitInsn(LXOR);
                methodVisitor.visitInsn(LOR);
            }
            extractBit(methodVisitor, index, is32Bit);
            methodVisitor.visitVarInsn(ISTORE, FLAG_SLOT);
        } else {
            methodVisitor.visitVarInsn(ALOAD, GLOB_SLOT);
            methodVisitor.visitFieldInsn(GETFIELD, globInternalName, fieldName, fieldDesc);
            methodVisitor.visitVarInsn(ASTORE, VALUE_SLOT);
        }

        methodVisitor.visitFieldInsn(GETSTATIC, callerName, functionName(field), FUNCTION_DESC);

        pushMask(methodVisitor, globInternalName, "isSet", is32Bit);
        extractBit(methodVisitor, index, is32Bit);

        Label nullLabel = new Label();
        Label done = new Label();
        if (primitive) {
            methodVisitor.visitVarInsn(ILOAD, FLAG_SLOT);
            methodVisitor.visitVarInsn(ILOAD, FLAG_SLOT);
            methodVisitor.visitJumpInsn(IFNE, nullLabel);
            methodVisitor.visitVarInsn(ALOAD, GLOB_SLOT);
            methodVisitor.visitFieldInsn(GETFIELD, globInternalName, fieldName, fieldDesc);
            if (spec.boxedOwner != null) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, spec.boxedOwner, "valueOf",
                        "(" + spec.nativeDesc + ")" + spec.valueDesc, false);
            }
            methodVisitor.visitJumpInsn(GOTO, done);
            methodVisitor.visitLabel(nullLabel);
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitLabel(done);
        } else {
            methodVisitor.visitVarInsn(ALOAD, VALUE_SLOT);
            methodVisitor.visitJumpInsn(IFNULL, nullLabel);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitJumpInsn(GOTO, done);
            methodVisitor.visitLabel(nullLabel);
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitLabel(done);
            methodVisitor.visitVarInsn(ALOAD, VALUE_SLOT);
        }

        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, FUNCTION, "call",
                "(ZZ" + OBJECT + OBJECT + OBJECT + ")V", true);
    }

    private static void pushMask(MethodVisitor methodVisitor, String globInternalName, String mask, boolean is32Bit) {
        methodVisitor.visitVarInsn(ALOAD, GLOB_SLOT);
        methodVisitor.visitFieldInsn(GETFIELD, globInternalName, mask, is32Bit ? "I" : "J");
    }

    /** (mask >>> index) & 1, i.e. the bit as the 0 / 1 an int-typed boolean argument wants. */
    private static void extractBit(MethodVisitor methodVisitor, int index, boolean is32Bit) {
        pushInt(methodVisitor, index);
        if (is32Bit) {
            methodVisitor.visitInsn(IUSHR);
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitInsn(IAND);
        } else {
            methodVisitor.visitInsn(LUSHR);
            methodVisitor.visitInsn(LCONST_1);
            methodVisitor.visitInsn(LAND);
            methodVisitor.visitInsn(L2I);
        }
    }

    private static void pushInt(MethodVisitor methodVisitor, int value) {
        if (value <= 5) {
            methodVisitor.visitInsn(ICONST_0 + value);
        } else if (value <= Byte.MAX_VALUE) {
            methodVisitor.visitIntInsn(BIPUSH, value);
        } else {
            methodVisitor.visitIntInsn(SIPUSH, value);
        }
    }
}
