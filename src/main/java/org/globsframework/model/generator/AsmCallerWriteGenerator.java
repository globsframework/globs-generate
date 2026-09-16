package org.globsframework.model.generator;

import org.globsframework.core.model.caller.CallerName;
import org.globsframework.core.model.caller.KeySource;
import org.globsframework.core.model.caller.ToGlobCaller;
import org.globsframework.core.model.caller.ToGlobCallerAll;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobFunction;
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
 * The to-Glob side of {@link AsmCallerGenerator} : the only implementation of
 * {@link ToGlobCallerFactory}, emitting one class per {@code create} call, holding one
 * {@code public static final ToGlobFunction} per entry and a {@code call} that dispatches to them from
 * a call site of its own.
 * <p>
 * Same reason as on the from-Glob side, and the same measurements behind it : a {@code static final} read is a
 * constant to the JIT, so each {@code INVOKEINTERFACE call} sees a single receiver type and inlines, where the
 * one call site of a hand-written {@code functions[next].call(...)} sees every function of every entry of
 * every type and stays megamorphic. That is also why a class is emitted per {@code create} rather than per
 * GlobType : two callers over the same type hold different functions, and sharing the class would put them
 * back on the same call sites.
 * <p>
 * What is *not* the same as the from-Glob side : nothing here reads the layout of a Glob. The functions do their
 * own writing through {@link org.globsframework.core.model.MutableGlob}, so there is no GlobType to look at,
 * no CHECKCAST to a generated Glob class, and no ClassLoader to borrow from a factory — a generated caller
 * only ever names core interfaces. It works whatever built the Glob it is handed, generated or not, and this
 * class is therefore usable on its own, without {@code globs.builder}.
 * <p>
 * A parser should not name it, though : {@code ToGlobCallerFactory.get()} answers this through
 * {@link AsmCallerWriteGeneratorService} when {@code -Dglobs.caller.toGlob} installs it, and core's looped
 * {@code LoopToGlobCallerFactory} otherwise — same behaviour, so one code path.
 *
 * <pre>
 * SortedMap&lt;Integer, ToGlobFunction&lt;In, Void, Void&gt;&gt; functions = new TreeMap&lt;&gt;();
 * // ... one per attribute of the format, keyed by whatever the parser answers for it
 * ToGlobCaller&lt;In, Void, Void&gt; caller =
 *         ToGlobCallerFactory.get().create("myformat.read", functions, skipUnknown, -1);
 * caller.call(parser, type.instantiate(), in, null, null);
 * </pre>
 *
 * The emitted {@code call} is one method, so it is the usual bytecode budget that caps how many entries are
 * worth unrolling : a case is a dozen bytes plus its switch entry, which leaves room for a few thousand, well
 * past the point where the JIT stops inlining any of it.
 * <p>
 * <b>The chunk</b>, on the unrolled caller only. An unrolled call is ~12 bytes (a GETSTATIC, four ALOAD and an
 * INVOKEINTERFACE), so past ~27 entries the emitted {@code call} is over {@code FreqInlineSize} (325) and C2
 * stops inlining it <em>as a whole</em> — the one method that was supposed to be folded into its caller
 * becomes a call. Emitting the entries in several private static parts of at most {@code chunk} each puts
 * every method back under the threshold : {@code call} is then three invokestatic, and each part inlines on
 * its own. It is a JIT knob and nothing else — same functions, same order, same result — so it is set per
 * process with {@code -Dglobs.caller.toGlob.chunk=<n>} (0, the default, emits one method as before) or per
 * generator with {@link #withChunk(int)}. The chunk is in the digest of the generated name : two chunk
 * sizes are two different classes, as they are two different sets of bytes.
 */
public class AsmCallerWriteGenerator implements ToGlobCallerFactory {
    /** What sets the chunk of the whole process, read by {@link #fromProperty()}. */
    public static final String CHUNK_PROPERTY = "globs.caller.toGlob.chunk";

    /**
     * Stateless : generation keys everything by the name of the class it emits, so one instance serves the
     * whole process. This one emits the unrolled caller as a single method, which is what it always did.
     */
    public static final AsmCallerWriteGenerator INSTANCE = new AsmCallerWriteGenerator(0);

    // one generator per chunk, since a generator is nothing but that number : keeps withChunk cheap enough
    // to be called per create, so that a changed property is picked up by the next caller built.
    private static final Map<Integer, AsmCallerWriteGenerator> BY_CHUNK = new ConcurrentHashMap<>();

    /** Entries per emitted method on the unrolled caller, 0 for one method whatever the count. */
    private final int chunk;

    private AsmCallerWriteGenerator(int chunk) {
        if (chunk < 0) {
            throw new IllegalArgumentException("A chunk is a number of entries per emitted method, or 0 for "
                                               + "one method : " + chunk);
        }
        this.chunk = chunk;
    }

    /**
     * @param chunk at most this many entries per emitted method on the unrolled caller; 0 to emit one method.
     *              Over ~27 a method is past {@code FreqInlineSize} and stops being inlined as a whole, so
     *              that is the top of the useful range rather than a limit enforced here.
     */
    public static AsmCallerWriteGenerator withChunk(int chunk) {
        return chunk == 0 ? INSTANCE : BY_CHUNK.computeIfAbsent(chunk, AsmCallerWriteGenerator::new);
    }

    /** What {@link AsmCallerWriteGeneratorService} answers : {@link #CHUNK_PROPERTY}, or no chunk. */
    public static AsmCallerWriteGenerator fromProperty() {
        String value = System.getProperty(CHUNK_PROPERTY);
        if (value == null || value.isBlank()) {
            return INSTANCE;
        }
        try {
            return withChunk(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("-D" + CHUNK_PROPERTY + " is a number of entries per emitted "
                                               + "method : " + value, e);
        }
    }

    private static final String GENERATOR = "org/globsframework/model/generator/AsmCallerWriteGenerator";
    private static final String CALLER_PKG = "org/globsframework/core/model/caller/";
    private static final String FUNCTION = CALLER_PKG + "ToGlobFunction";
    private static final String FUNCTION_DESC = "L" + FUNCTION + ";";
    private static final String FUNCTIONS_DESC = "[" + FUNCTION_DESC;
    private static final String KEY_SOURCE = CALLER_PKG + "KeySource";
    private static final String CALLER = CALLER_PKG + "ToGlobCaller";
    private static final String CALLER_ALL = CALLER_PKG + "ToGlobCallerAll";
    private static final String CALLER_FACTORY = CALLER_PKG + "ToGlobCallerFactory";
    private static final String OBJECT = "Ljava/lang/Object;";
    private static final String MUTABLE_GLOB = "Lorg/globsframework/core/model/MutableGlob;";
    /** the erasure of ToGlobFunction.call, and of ToGlobCallerAll.call */
    private static final String CALL_DESC = "(" + MUTABLE_GLOB + OBJECT + OBJECT + OBJECT + ")V";
    private static final String GEN_PACKAGE = "org/globsframework/gen/toglob/";

    // ToGlobCaller.call : 0 this, 1 keySource, 2 data, 3-5 the contexts, 6 what nextKey answered
    private static final int NEXT_SLOT = 6;

    // What the generated caller's <clinit> reads, keyed by the name of the class that reads it -- a name
    // GeneratedName has already made unique, so two creations never race over one entry. Same protocol as
    // the PENDING map of the other generators : the entry lives only for the duration of create, so nothing
    // here keeps a function -- nor the ClassLoader of the generated class -- alive.
    private static final Map<String, ToGlobFunction[]> PENDING = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <C1, C2, C3> ToGlobCaller<C1, C2, C3> create(
            String name, SortedMap<Integer, ToGlobFunction<C1, C2, C3>> functions,
            ToGlobFunction fallback, int endLoop) {
        CallerName.check(name);
        // sorted here rather than trusted : a lookupswitch wants its keys ascending, and the map may have
        // been built with a comparator of its own
        int[] keys = functions.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        ToGlobFunction[] all = new ToGlobFunction[keys.length + (fallback != null ? 1 : 0)];
        for (int i = 0; i < keys.length; i++) {
            all[i] = ToGlobCallerFactory.checked(functions.get(keys[i]), "key " + keys[i]);
        }
        if (fallback != null) {
            all[keys.length] = fallback;
        }
        // the shape is in the digest, not only the name : nothing here is a GlobType, so a parser giving one
        // name per type is what tells two callers apart, and two shapes under one name still have to be two
        // classes -- with names that say so rather than an order of creation
        String callerName = GEN_PACKAGE + GeneratedName.unique("Caller", new String[]{name},
                name, Arrays.toString(keys), Boolean.toString(fallback != null), Integer.toString(endLoop));
        return (ToGlobCaller<C1, C2, C3>)
                generate(callerName, all, () -> generateCaller(callerName, keys, fallback != null, endLoop));
    }

    @SuppressWarnings("unchecked")
    public <C1, C2, C3> ToGlobCallerAll<C1, C2, C3> create(
            String name, ToGlobFunction<C1, C2, C3>[] functions) {
        CallerName.check(name);
        ToGlobFunction[] all = new ToGlobFunction[functions.length];
        for (int i = 0; i < functions.length; i++) {
            all[i] = ToGlobCallerFactory.checked(functions[i], "index " + i);
        }
        // the chunk that will really be emitted, not the one asked for : below it nothing is split, and the
        // bytes -- hence the name -- are those of a caller built without a chunk at all
        int emitted = chunk == 0 || all.length <= chunk ? 0 : chunk;
        String callerName = GEN_PACKAGE + GeneratedName.unique("CallerAll", new String[]{name},
                name, Integer.toString(all.length), Integer.toString(emitted));
        return (ToGlobCallerAll<C1, C2, C3>)
                generate(callerName, all, () -> generateCallerAll(callerName, all.length, emitted));
    }

    /**
     * Defines the caller in {@link GeneratedClassLoader} — it only has core interfaces to resolve, so it
     * needs nothing of what is generated around it — and instantiates it, which is what runs the
     * {@code <clinit>} reading PENDING.
     */
    private static Object generate(String callerName, ToGlobFunction[] functions,
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
    public static ToGlobFunction[] getFunctions(String callerName) {
        ToGlobFunction[] functions = PENDING.get(callerName);
        if (functions == null) {
            throw new IllegalStateException("Nothing registered for generated write caller " + callerName
                                            + " : the generated class was initialized outside of create.");
        }
        return functions;
    }

    /**
     * {@code while ((next = keySource.nextKey()) != endLoop) switch (next) { case k -> fn_i.call(...); }}
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
                "(L" + KEY_SOURCE + ";" + MUTABLE_GLOB + OBJECT + OBJECT + OBJECT + ")V", null, null);
        methodVisitor.visitCode();

        Label top = new Label();
        Label end = new Label();
        Label dflt = new Label();
        Label[] labels = new Label[keys.length];
        Arrays.setAll(labels, i -> new Label());

        methodVisitor.visitLabel(top);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, KEY_SOURCE, "nextKey", "()I", true);
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

    /**
     * The same functions, no input to follow : the array unrolled, one call site per element.
     * <p>
     * In one method when {@code chunk} is 0, else in parts of at most {@code chunk} entries that {@code call}
     * invokes in order — same functions, same order, only the method they sit in changes. The parts are
     * {@code private static} because everything they touch is : the functions are static fields.
     */
    static byte[] generateCallerAll(String callerName, int count, int chunk) {
        ClassWriter classWriter = newClassWriter(callerName, CALLER_ALL);
        declareFunctions(classWriter, count, false);
        generateInit(classWriter);
        generateClinit(classWriter, callerName, count, false);

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "call", CALL_DESC,
                null, null);
        methodVisitor.visitCode();
        if (chunk == 0) {
            for (int i = 0; i < count; i++) {
                emitCall(methodVisitor, callerName, functionName(i), 1);
            }
        } else {
            for (int part = 0; part * chunk < count; part++) {
                emitPartCall(methodVisitor, callerName, partName(part), 1);
            }
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        if (chunk != 0) {
            for (int part = 0; part * chunk < count; part++) {
                generatePart(classWriter, callerName, partName(part), part * chunk,
                        Math.min(part * chunk + chunk, count));
            }
        }

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    /** One part of a chunked unrolled caller : the entries of {@code [from, to)}, in order. */
    private static void generatePart(ClassWriter classWriter, String callerName, String part, int from, int to) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PRIVATE | ACC_STATIC, part, CALL_DESC,
                null, null);
        methodVisitor.visitCode();
        for (int i = from; i < to; i++) {
            emitCall(methodVisitor, callerName, functionName(i), 0);
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
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

    /** {@code part(data, ctx1, ctx2, ctx3)}, the Glob and the contexts starting at {@code dataSlot}. */
    private static void emitPartCall(MethodVisitor methodVisitor, String callerName, String part, int dataSlot) {
        for (int slot = dataSlot; slot < dataSlot + 4; slot++) {
            methodVisitor.visitVarInsn(ALOAD, slot);
        }
        methodVisitor.visitMethodInsn(INVOKESTATIC, callerName, part, CALL_DESC, false);
    }

    private static String functionName(int index) {
        return "fn_" + index;
    }

    private static String partName(int index) {
        return "part_" + index;
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
