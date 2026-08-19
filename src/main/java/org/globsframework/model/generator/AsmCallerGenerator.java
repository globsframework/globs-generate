package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.caller.CallerName;
import org.globsframework.core.model.caller.FromGlobFunction;
import org.globsframework.core.model.caller.FromGlobCallerFactory;
import org.globsframework.core.model.caller.LoopFromGlobCaller;
import org.globsframework.core.model.caller.FromGlobCaller;
import org.globsframework.core.model.impl.AbstractDefaultGlob;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.objectweb.asm.Opcodes.*;

/**
 * Generates the {@link FromGlobCaller} of one GlobType : a class holding one
 * {@code public static final FromGlobFunction} per field, and a {@code call} unrolled over them.
 * <p>
 * The point is not to save the loop — it is to give the JVM one call site per field instead of one for all
 * of them. A {@code static final} read is a constant to the JIT, so each {@code INVOKEINTERFACE call} sees a
 * single receiver type and inlines, where the loop of a hand-written serializer sees every function of every
 * field of every type and stays megamorphic. That is also why a class is emitted per
 * {@link FromGlobCallerFactory#create} rather than per type : two callers over the same type hold different
 * functions, and sharing the class would put them back on the same call sites. The class is named after the
 * purpose the caller gives ({@link CallerName}) and a digest of what it is generated over, so that the same
 * codec over the same type gets the same class name in every run — see {@link GeneratedName}.
 * <p>
 * Like {@link AsmAccessorGenerator} this reads the value fields and the masks of the generated Glob straight
 * out of another package (they are {@code public} for exactly that reason), and it is written with
 * COMPUTE_FRAMES : the null handling is branchy and hand-computed frames would buy nothing but VerifyErrors.
 * <p>
 * Boxing : the interface is generic, so a primitive-flavour value has to be boxed to be passed. Since the
 * call site is monomorphic and small, the box normally dies in escape analysis once the function is inlined —
 * but it is a real allocation whenever it is not.
 */
public class AsmCallerGenerator {
    private static final String GENERATOR = "org/globsframework/model/generator/AsmCallerGenerator";
    private static final String FUNCTION = "org/globsframework/core/model/caller/FromGlobFunction";
    private static final String FUNCTION_DESC = "L" + FUNCTION + ";";
    private static final String FUNCTIONS_DESC = "[" + FUNCTION_DESC;
    private static final String CALLER = "org/globsframework/core/model/caller/FromGlobCaller";
    private static final String GLOB = "Lorg/globsframework/core/model/Glob;";
    private static final String OBJECT = "Ljava/lang/Object;";
    private static final String GEN_PACKAGE = "org/globsframework/gen/fromglob/";

    // the Glob is cast once into slot 4; 5 and 6 are the per-field scratch (the null flag / the value)
    private static final int GLOB_SLOT = 4;
    private static final int FLAG_SLOT = 5;
    private static final int VALUE_SLOT = 6;

    // What the generated caller's <clinit> reads, keyed by the name of the class that reads it -- a name
    // GeneratedName has already made unique, so two creations never race over one entry. Same protocol as
    // the PENDING map of the two generators : the entry lives only for the duration of create, so nothing
    // here keeps a function -- nor the ClassLoader of the generated class -- alive.
    private static final Map<String, FromGlobFunction[]> PENDING = new ConcurrentHashMap<>();

    /** How the emitted {@code call} gets isSet / isNull / the value out of the Glob it was cast to. */
    private enum Access {
        /** generated Glob, object flavour : public value fields, one isSet mask, null is the value being null */
        OBJECT,
        /** generated Glob, primitive flavour : native value fields, isSet *and* isNull masks */
        PRIMITIVE,
        /** core's DefaultGlob32/64/128 : the values array through get(int), the mask through isSetAt(int) */
        DEFAULT_GLOB
    }

    /**
     * The FromGlobCallerFactory handed to the factory of a generated type. It is built here rather than by the
     * factory because only the generator knows what it is generating over — the Glob class and its flavour —
     * exactly like the AccessorProvider next to it.
     *
     * @param globInternalName the generated Glob class, whose public fields and masks the caller reads
     * @param primitive        which flavour that Glob is : native fields plus an isNull mask, or boxed ones
     */
    public static FromGlobCallerFactory generatorFor(GeneratedClassLoader globLoader, String globInternalName, GlobType type,
                                              boolean primitive) {
        Access access = primitive ? Access.PRIMITIVE : Access.OBJECT;
        return new FromGlobCallerFactory() {
            public <C1, C2> FromGlobCaller<C1, C2> create(String name, Functions<C1, C2> functions) {
                return generate(globLoader, globInternalName, type, access, name, functions);
            }
        };
    }

    /**
     * The same unrolled caller, over a Glob this module did **not** generate : core's DefaultGlob32/64/128.
     * <p>
     * Nothing is generated for the type itself — no Glob class, no accessors, so the application's own
     * {@code glob.get(F)} keeps going through core's {@code values[field.getIndex()]}, which is a handful of
     * bytecodes and always inlines. Only the traversal a codec does is generated, and it reads the values
     * array straight through {@code AbstractDefaultGlob.get(int)} (final, an array load once inlined) and the
     * set mask through {@code isSetAt(int)} on the *exact* concrete class (final, one mask test) — no Field
     * object, no {@code getIndex()}, no interface dispatch, no tableswitch.
     * <p>
     * That makes it the option for an application that measured the generated Globs to be a loss : the
     * per-field call sites of the codec become monomorphic without the metamodel of the whole process
     * gaining one Glob class per type.
     *
     * @return null when the type's factory does not build an AbstractDefaultGlob — nothing here can read it,
     * and a caller of {@link FromGlobCallerFactory#callerFor} should fall back to {@link LoopFromGlobCaller}.
     */
    public static FromGlobCallerFactory forDefaultGlob(GlobType type) {
        Class<?> globClass = type.instantiate().getClass();
        if (!AbstractDefaultGlob.class.isAssignableFrom(globClass)) {
            return null;
        }
        String globInternalName = globClass.getName().replace('.', '/');
        // the concrete Glob is an ordinary core class, so this caller resolves it through the loader's
        // parent -- but it is defined in the same loader as everything else this module generates
        GeneratedClassLoader loader = GeneratedClassLoader.get();
        return new FromGlobCallerFactory() {
            public <C1, C2> FromGlobCaller<C1, C2> create(String name, Functions<C1, C2> functions) {
                return generate(loader, globInternalName, type, Access.DEFAULT_GLOB, name, functions);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <C1, C2> FromGlobCaller<C1, C2> generate(GeneratedClassLoader loader, String globInternalName,
                                                                 GlobType type, Access access, String name,
                                                                 FromGlobCallerFactory.Functions<C1, C2> provider) {
        CallerName.check(name);
        Field[] fields = type.getFields();
        FromGlobFunction[] functions = new FromGlobFunction[fields.length];
        for (Field field : fields) {
            FromGlobFunction<?, C1, C2> function = provider.forField(field);
            if (function == null) {
                throw new IllegalArgumentException("No FromGlobFunction for " + field.getName()
                                                   + " of " + type.getName());
            }
            functions[field.getIndex()] = function;
        }

        String callerName = getCallerName(globInternalName, access, type, name);
        // the same loader as the Glob it reads : it sees that class without a child loader, which is what
        // the caller used to need one for
        loader.emit(callerName, () -> generateCaller(callerName, globInternalName, type, access));

        PENDING.put(callerName, functions);
        try {
            // newInstance triggers the <clinit> that reads PENDING
            return (FromGlobCaller<C1, C2>) loader.load(callerName)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Can not generate the caller of " + type.getName() + " : " + e.getMessage(), e);
        } finally {
            PENDING.remove(callerName);
        }
    }

    /** Called from the generated caller's {@code <clinit>}, which runs while generate is still on the stack. */
    public static FromGlobFunction[] getFunctions(String callerName) {
        FromGlobFunction[] functions = PENDING.get(callerName);
        if (functions == null) {
            throw new IllegalStateException("Nothing registered for generated caller " + callerName
                                            + " : the generated class was initialized outside of create.");
        }
        return functions;
    }

    /**
     * The name of the emitted class : the purpose the caller gave and the type it walks, readable, plus a
     * digest of everything the bytes depend on — the Glob class the CHECKCAST names, the flavour, and the
     * layout the unrolled call is written against.
     * <p>
     * One package for every read caller, whatever it walks : which flavour of Glob is in the digest, and the
     * generated Glob's own package was never anything the emitted code needed — it reads public fields.
     * <p>
     * The Glob class is in the digest because it is what the emitted code reads : until the Glob classes
     * themselves are named this way, a caller over a generated Glob inherits their per-run numbering, while
     * a caller over core's DefaultGlob is already the same in every run.
     */
    static String getCallerName(String globInternalName, Access access, GlobType type, String name) {
        String simple = globInternalName.substring(globInternalName.lastIndexOf('/') + 1);
        return GEN_PACKAGE + GeneratedName.unique("Caller",
                new String[]{name, GeneratedName.simpleName(type.getName())},
                name, type.getName(), simple, access.name(), layout(type));
    }

    /** Everything of the type the emitted call is written against : the field it reads, and how. */
    private static String layout(GlobType type) {
        StringBuilder builder = new StringBuilder();
        for (Field field : type.getFields()) {
            builder.append(field.getIndex()).append(':')
                    .append(AsmFactoryGenerator.fieldName(field)).append(':')
                    .append(field.getDataType()).append(';');
        }
        return builder.toString();
    }

    private static String functionName(Field field) {
        return "fn_" + field.getIndex();
    }

    static byte[] generateCaller(String callerName, String globInternalName, GlobType type, Access access) {
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
            // its own name, which is the key it was registered under : a constant the class already implies,
            // so the bytes stay a pure function of what the name digests
            methodVisitor.visitLdcInsn(callerName);
            methodVisitor.visitMethodInsn(INVOKESTATIC, GENERATOR, "getFunctions",
                    "(Ljava/lang/String;)" + FUNCTIONS_DESC, false);
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
                    if (access == Access.DEFAULT_GLOB) {
                        emitDefaultGlobFieldCall(methodVisitor, callerName, globInternalName, field);
                    } else {
                        emitFieldCall(methodVisitor, callerName, globInternalName, field,
                                access == Access.PRIMITIVE, is32Bit);
                    }
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

    /**
     * The same call, over one of core's DefaultGlob32/64/128/DefaultGlob.
     * <p>
     * Everything is a field read : the value is {@code values[index]} out of AbstractDefaultGlob's array, and
     * the set bit comes from the mask of the *exact* concrete class. Both are {@code public} in core for
     * exactly this — the same bet the generated Globs already make for their own accessors. No Field object,
     * no getIndex(), no virtual call, no tableswitch.
     * <p>
     * The mask is read the way its own class writes it, which is why the shape is per class : an {@code int}
     * for DefaultGlob32, a {@code long} for DefaultGlob64, one of two longs (and a shift of index-64) for
     * DefaultGlob128. Above 128 fields core keeps a BitSet, which has no bit to GETFIELD — that one still
     * goes through {@code BitSet.get(int)}, on the field rather than through isSetAt.
     * <p>
     * isNull is the value being null, which is exactly what core answers : {@code Glob.isNull(field)} is
     * {@code doCheckedGet(field) == null}, i.e. the same array slot. So the three arguments agree with
     * {@link LoopFromGlobCaller} field by field, including the unset case (not set, null, no value).
     */
    private static void emitDefaultGlobFieldCall(MethodVisitor methodVisitor, String callerName,
                                                 String globInternalName, Field field) {
        int index = field.getIndex();

        methodVisitor.visitVarInsn(ALOAD, GLOB_SLOT);
        methodVisitor.visitFieldInsn(GETFIELD, globInternalName, "values", "[" + OBJECT);
        pushInt(methodVisitor, index);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, VALUE_SLOT);

        methodVisitor.visitFieldInsn(GETSTATIC, callerName, functionName(field), FUNCTION_DESC);

        emitDefaultGlobIsSet(methodVisitor, globInternalName, index);

        Label nullLabel = new Label();
        Label done = new Label();
        methodVisitor.visitVarInsn(ALOAD, VALUE_SLOT);
        methodVisitor.visitJumpInsn(IFNULL, nullLabel);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitJumpInsn(GOTO, done);
        methodVisitor.visitLabel(nullLabel);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitLabel(done);
        methodVisitor.visitVarInsn(ALOAD, VALUE_SLOT);

        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, FUNCTION, "call",
                "(ZZ" + OBJECT + OBJECT + OBJECT + ")V", true);
    }

    /**
     * Pushes the set bit of {@code index} as the 0 / 1 an int-typed boolean argument wants, reading the mask
     * field of the concrete class. Branchless, and the same bit its own setSetAt writes.
     */
    private static void emitDefaultGlobIsSet(MethodVisitor methodVisitor, String globInternalName, int index) {
        methodVisitor.visitVarInsn(ALOAD, GLOB_SLOT);
        switch (globInternalName.substring(globInternalName.lastIndexOf('/') + 1)) {
            case "DefaultGlob32" -> {
                methodVisitor.visitFieldInsn(GETFIELD, globInternalName, "set", "I");
                extractBit(methodVisitor, index, true);
            }
            case "DefaultGlob64" -> {
                methodVisitor.visitFieldInsn(GETFIELD, globInternalName, "set", "J");
                extractBit(methodVisitor, index, false);
            }
            case "DefaultGlob128" -> {
                // two longs, the second one holding the bits of index 64 and above
                methodVisitor.visitFieldInsn(GETFIELD, globInternalName,
                        index < 64 ? "set1" : "set2", "J");
                extractBit(methodVisitor, index < 64 ? index : index - 64, false);
            }
            // above 128 fields core keeps a BitSet : there is no bit to read, only its get(int)
            default -> {
                methodVisitor.visitFieldInsn(GETFIELD, globInternalName, "isSet", "Ljava/util/BitSet;");
                pushInt(methodVisitor, index);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/BitSet", "get", "(I)Z", false);
            }
        }
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
