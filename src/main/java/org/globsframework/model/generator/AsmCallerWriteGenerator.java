package org.globsframework.model.generator;

import org.globsframework.core.model.generate.CallerName;
import org.globsframework.core.model.generate.write.CallAtWrite;
import org.globsframework.core.model.generate.write.GeneratedCallerWrite;
import org.globsframework.core.model.generate.write.GeneratedCallerWriteAll;
import org.globsframework.core.model.generate.write.GeneratedFunctionCallerWrite;
import org.globsframework.core.model.generate.write.MutableFunctionWrite;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.objectweb.asm.Opcodes.*;

/**
 * The write side of {@link AsmCallerGenerator} : the only implementation of
 * {@link GeneratedFunctionCallerWrite}, emitting one class per {@code create} call, holding one
 * {@code public static final MutableFunctionWrite} per entry and a {@code call} that dispatches to them from
 * a call site of its own.
 * <p>
 * Same reason as on the read side, and the same measurements behind it : a {@code static final} read is a
 * constant to the JIT, so each {@code INVOKEINTERFACE call} sees a single receiver type and inlines, where the
 * one call site of a hand-written {@code functions[next].call(...)} sees every function of every entry of
 * every type and stays megamorphic. That is also why a class is emitted per {@code create} rather than per
 * GlobType : two callers over the same type hold different functions, and sharing the class would put them
 * back on the same call sites.
 * <p>
 * What is *not* the same as the read side : nothing here reads the layout of a Glob. The functions do their
 * own writing through {@link org.globsframework.core.model.MutableGlob}, so there is no GlobType to look at,
 * no CHECKCAST to a generated Glob class, and no ClassLoader to borrow from a factory — a generated caller
 * only ever names core interfaces. It works whatever built the Glob it is handed, generated or not, and this
 * class is therefore usable on its own, without {@code globs.builder}.
 * <p>
 * A parser should not name it, though : {@code GeneratedFunctionCallerWrite.get()} answers this through
 * {@link AsmCallerWriteGeneratorService} when {@code -Dglobs.callerWrite} installs it, and core's looped
 * {@code DefaultFunctionCallerWrite} otherwise — same behaviour, so one code path.
 *
 * <pre>
 * SortedMap&lt;Integer, MutableFunctionWrite&lt;In, Void, Void&gt;&gt; functions = new TreeMap&lt;&gt;();
 * // ... one per attribute of the format, keyed by whatever the parser answers for it
 * GeneratedCallerWrite&lt;In, Void, Void&gt; caller =
 *         GeneratedFunctionCallerWrite.get().create("myformat.read", functions, skipUnknown, -1);
 * caller.call(parser, type.instantiate(), in, null, null);
 * </pre>
 *
 * The emitted {@code call} is one method, so it is the usual bytecode budget that caps how many entries are
 * worth unrolling : a case is a dozen bytes plus its switch entry, which leaves room for a few thousand, well
 * past the point where the JIT stops inlining any of it.
 */
public class AsmCallerWriteGenerator implements GeneratedFunctionCallerWrite {
    /** Stateless : generation keys everything by the name of the class it emits, so one instance serves the whole process. */
    public static final AsmCallerWriteGenerator INSTANCE = new AsmCallerWriteGenerator();

    private static final String GENERATOR = "org/globsframework/model/generator/AsmCallerWriteGenerator";
    private static final String WRITE = "org/globsframework/core/model/generate/write/";
    private static final String FUNCTION = WRITE + "MutableFunctionWrite";
    private static final String FUNCTION_DESC = "L" + FUNCTION + ";";
    private static final String FUNCTIONS_DESC = "[" + FUNCTION_DESC;
    private static final String CALL_AT = WRITE + "CallAtWrite";
    private static final String CALLER = WRITE + "GeneratedCallerWrite";
    private static final String CALLER_ALL = WRITE + "GeneratedCallerWriteAll";
    private static final String CALLER_FACTORY = WRITE + "GeneratedFunctionCallerWrite";
    private static final String OBJECT = "Ljava/lang/Object;";
    private static final String MUTABLE_GLOB = "Lorg/globsframework/core/model/MutableGlob;";
    /** the erasure of MutableFunctionWrite.call, and of GeneratedCallerWriteAll.call */
    private static final String CALL_DESC = "(" + MUTABLE_GLOB + OBJECT + OBJECT + OBJECT + ")V";
    private static final String CALLER_PACKAGE = "org/globsframework/gen/write/";

    // GeneratedCallerWrite.call : 0 this, 1 callAt, 2 data, 3-5 the contexts, 6 what getNextToCall answered
    private static final int NEXT_SLOT = 6;

    // What the generated caller's <clinit> reads, keyed by the name of the class that reads it -- a name
    // GeneratedName has already made unique, so two creations never race over one entry. Same protocol as
    // the PENDING map of the other generators : the entry lives only for the duration of create, so nothing
    // here keeps a function -- nor the ClassLoader of the generated class -- alive.
    private static final Map<String, MutableFunctionWrite[]> PENDING = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <Ctx1, Ctx2, Ctx3> GeneratedCallerWrite<Ctx1, Ctx2, Ctx3> create(
            String name, SortedMap<Integer, MutableFunctionWrite<Ctx1, Ctx2, Ctx3>> functions,
            MutableFunctionWrite fallback, int endLoop) {
        CallerName.check(name);
        // sorted here rather than trusted : a lookupswitch wants its keys ascending, and the map may have
        // been built with a comparator of its own
        int[] keys = functions.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        MutableFunctionWrite[] all = new MutableFunctionWrite[keys.length + (fallback != null ? 1 : 0)];
        for (int i = 0; i < keys.length; i++) {
            all[i] = GeneratedFunctionCallerWrite.checked(functions.get(keys[i]), "key " + keys[i]);
        }
        if (fallback != null) {
            all[keys.length] = fallback;
        }
        // the shape is in the digest, not only the name : nothing here is a GlobType, so a parser giving one
        // name per type is what tells two callers apart, and two shapes under one name still have to be two
        // classes -- with names that say so rather than an order of creation
        String callerName = CALLER_PACKAGE + GeneratedName.unique("Caller", new String[]{name},
                name, Arrays.toString(keys), Boolean.toString(fallback != null), Integer.toString(endLoop));
        return (GeneratedCallerWrite<Ctx1, Ctx2, Ctx3>)
                generate(callerName, all, () -> generateCaller(callerName, keys, fallback != null, endLoop));
    }

    @SuppressWarnings("unchecked")
    public <Ctx1, Ctx2, Ctx3> GeneratedCallerWriteAll<Ctx1, Ctx2, Ctx3> create(
            String name, MutableFunctionWrite<Ctx1, Ctx2, Ctx3>[] functions) {
        CallerName.check(name);
        MutableFunctionWrite[] all = new MutableFunctionWrite[functions.length];
        for (int i = 0; i < functions.length; i++) {
            all[i] = GeneratedFunctionCallerWrite.checked(functions[i], "index " + i);
        }
        String callerName = CALLER_PACKAGE + GeneratedName.unique("CallerAll", new String[]{name},
                name, Integer.toString(all.length));
        return (GeneratedCallerWriteAll<Ctx1, Ctx2, Ctx3>)
                generate(callerName, all, () -> generateCallerAll(callerName, all.length));
    }

    /**
     * Defines the caller in {@link GeneratedClassLoader} — it only has core interfaces to resolve, so it
     * needs nothing of what is generated around it — and instantiates it, which is what runs the
     * {@code <clinit>} reading PENDING.
     */
    private static Object generate(String callerName, MutableFunctionWrite[] functions,
                                   Supplier<byte[]> bytes) {
        GeneratedClassLoader loader = GeneratedClassLoader.get();
        loader.emit(callerName, bytes);
        PENDING.put(callerName, functions);
        try {
            return loader.load(callerName).getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Can not generate " + callerName + " : " + e.getMessage(), e);
        } finally {
            PENDING.remove(callerName);
        }
    }

    /** Called from the generated caller's {@code <clinit>}, which runs while generate is still on the stack. */
    public static MutableFunctionWrite[] getFunctions(String callerName) {
        MutableFunctionWrite[] functions = PENDING.get(callerName);
        if (functions == null) {
            throw new IllegalStateException("Nothing registered for generated write caller " + callerName
                                            + " : the generated class was initialized outside of create.");
        }
        return functions;
    }

    /**
     * {@code while ((next = callAt.getNextToCall()) != endLoop) switch (next) { case k -> fn_i.call(...); }}
     * <p>
     * The endLoop test comes first, so that value never reaches the switch : it may be a key of the map, and
     * ending wins. The switch is a tableswitch when the keys are dense enough to pay for the holes, a
     * lookupswitch otherwise — the same trade javac makes.
     */
    static byte[] generateCaller(String callerName, int[] keys, boolean hasFallback, int endLoop) {
        ClassWriter classWriter = newClassWriter(callerName, CALLER);
        declareFunctions(classWriter, keys.length, hasFallback);
        generateInit(classWriter);
        generateClinit(classWriter, callerName, keys.length, hasFallback);

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "call",
                "(L" + CALL_AT + ";" + MUTABLE_GLOB + OBJECT + OBJECT + OBJECT + ")V", null, null);
        methodVisitor.visitCode();

        Label top = new Label();
        Label end = new Label();
        Label dflt = new Label();
        Label[] labels = new Label[keys.length];
        Arrays.setAll(labels, i -> new Label());

        methodVisitor.visitLabel(top);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, CALL_AT, "getNextToCall", "()I", true);
        methodVisitor.visitVarInsn(ISTORE, NEXT_SLOT);
        methodVisitor.visitVarInsn(ILOAD, NEXT_SLOT);
        pushInt(methodVisitor, endLoop);
        methodVisitor.visitJumpInsn(IF_ICMPEQ, end);

        methodVisitor.visitVarInsn(ILOAD, NEXT_SLOT);
        if (useTableSwitch(keys)) {
            int lo = keys[0];
            int hi = keys[keys.length - 1];
            Label[] table = new Label[hi - lo + 1];
            Arrays.fill(table, dflt);
            for (int i = 0; i < keys.length; i++) {
                table[keys[i] - lo] = labels[i];
            }
            methodVisitor.visitTableSwitchInsn(lo, hi, dflt, table);
        } else {
            methodVisitor.visitLookupSwitchInsn(dflt, keys, labels);
        }

        for (int i = 0; i < keys.length; i++) {
            methodVisitor.visitLabel(labels[i]);
            emitCall(methodVisitor, callerName, functionName(i), 2);
            methodVisitor.visitJumpInsn(GOTO, top);
        }

        methodVisitor.visitLabel(dflt);
        if (hasFallback) {
            emitCall(methodVisitor, callerName, "fallback", 2);
            methodVisitor.visitJumpInsn(GOTO, top);
        } else {
            // core's, not one of ours : the loop and the generated caller say the same thing for the same key
            methodVisitor.visitVarInsn(ILOAD, NEXT_SLOT);
            methodVisitor.visitMethodInsn(INVOKESTATIC, CALLER_FACTORY, "unknownKey",
                    "(I)Ljava/lang/RuntimeException;", true);
            methodVisitor.visitInsn(ATHROW);
        }

        methodVisitor.visitLabel(end);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    /** The same functions, no input to follow : the array unrolled, one call site per element. */
    static byte[] generateCallerAll(String callerName, int count) {
        ClassWriter classWriter = newClassWriter(callerName, CALLER_ALL);
        declareFunctions(classWriter, count, false);
        generateInit(classWriter);
        generateClinit(classWriter, callerName, count, false);

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "call", CALL_DESC,
                null, null);
        methodVisitor.visitCode();
        for (int i = 0; i < count; i++) {
            emitCall(methodVisitor, callerName, functionName(i), 1);
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static ClassWriter newClassWriter(String callerName, String itf) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            // the loop needs frames, and computing them must not send ASM looking for the class it is in the
            // middle of generating : nothing merged here needs a precise common supertype
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        classWriter.visit(V17, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, callerName, null, "java/lang/Object",
                new String[]{itf});
        return classWriter;
    }

    private static void declareFunctions(ClassWriter classWriter, int count, boolean hasFallback) {
        for (int i = 0; i < count; i++) {
            classWriter.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, functionName(i), FUNCTION_DESC,
                    null, null).visitEnd();
        }
        if (hasFallback) {
            classWriter.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "fallback", FUNCTION_DESC,
                    null, null).visitEnd();
        }
    }

    private static void generateInit(ClassWriter classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    /** Fills the statics from the array registered under the class name, the fallback being its last element. */
    private static void generateClinit(ClassWriter classWriter, String callerName, int count,
                                       boolean hasFallback) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        methodVisitor.visitCode();
        // its own name, which is the key it was registered under : a constant the class already implies, so
        // the bytes stay a pure function of what the name digests
        methodVisitor.visitLdcInsn(callerName);
        methodVisitor.visitMethodInsn(INVOKESTATIC, GENERATOR, "getFunctions",
                "(Ljava/lang/String;)" + FUNCTIONS_DESC, false);
        methodVisitor.visitVarInsn(ASTORE, 0);
        for (int i = 0; i < count; i++) {
            methodVisitor.visitVarInsn(ALOAD, 0);
            pushInt(methodVisitor, i);
            methodVisitor.visitInsn(AALOAD);
            methodVisitor.visitFieldInsn(PUTSTATIC, callerName, functionName(i), FUNCTION_DESC);
        }
        if (hasFallback) {
            methodVisitor.visitVarInsn(ALOAD, 0);
            pushInt(methodVisitor, count);
            methodVisitor.visitInsn(AALOAD);
            methodVisitor.visitFieldInsn(PUTSTATIC, callerName, "fallback", FUNCTION_DESC);
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    /** {@code fn.call(data, ctx1, ctx2, ctx3)}, the Glob and the contexts starting at {@code dataSlot}. */
    private static void emitCall(MethodVisitor methodVisitor, String callerName, String function, int dataSlot) {
        methodVisitor.visitFieldInsn(GETSTATIC, callerName, function, FUNCTION_DESC);
        for (int slot = dataSlot; slot < dataSlot + 4; slot++) {
            methodVisitor.visitVarInsn(ALOAD, slot);
        }
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, FUNCTION, "call", CALL_DESC, true);
    }

    /**
     * A tableswitch is a jump through a table — one indirection whatever the number of keys — where a
     * lookupswitch is a binary search, so it is worth padding the holes as long as they stay a small part of
     * the table. Same rule of thumb as javac's, in long arithmetic because the span of two ints overflows.
     */
    private static boolean useTableSwitch(int[] keys) {
        if (keys.length == 0) {
            return false;
        }
        long span = (long) keys[keys.length - 1] - keys[0] + 1;
        return span <= 2L * keys.length + 8;
    }

    private static String functionName(int index) {
        return "fn_" + index;
    }

    /** Any int, unlike the read generator's : a key or an endLoop value can be negative or large. */
    private static void pushInt(MethodVisitor methodVisitor, int value) {
        if (value >= -1 && value <= 5) {
            methodVisitor.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            methodVisitor.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            methodVisitor.visitIntInsn(SIPUSH, value);
        } else {
            methodVisitor.visitLdcInsn(value);
        }
    }
}
