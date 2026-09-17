package org.globsframework.model.generator;

import org.globsframework.core.model.caller.CallerName;
import org.globsframework.core.model.caller.CallerShape;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.objectweb.asm.Opcodes.*;

/**
 * The to-Glob side of {@link AsmCallerGenerator} : the only implementation of {@link ToGlobCallerFactory},
 * emitting one class per {@code create} call, holding one {@code public static final} function per entry and
 * one method that dispatches to them from a call site of its own.
 * <p>
 * Same reason as on the from-Glob side, and the same measurements behind it : a {@code static final} read is a
 * constant to the JIT, so each call sees a single receiver type and inlines, where the one call site of a
 * hand-written {@code functions[next].call(...)} sees every function of every entry of every type and stays
 * megamorphic. That is also why a class is emitted per {@code create} rather than per GlobType : two callers
 * over the same type hold different functions, and sharing the class would put them back on the same call
 * sites.
 * <p>
 * <b>Everything is emitted over the caller's own interfaces.</b> {@code tClass} is what the emitted class
 * implements and {@code dClass} is what it calls, so the arguments are whatever the parser passes around —
 * a {@code long} is an LLOAD into the callee's own slot, and a function is the object the caller already had.
 * There is no generic {@code ToGlobCaller}/{@code ToGlobFunction} pair any more : carrying three
 * {@code Object} contexts cost a box per primitive, a bridge method in front of every function of another
 * shape and one call level more — four times what generating the dispatch earns back, measured in
 * globs-off-heap. The method to emit and the method to call come from core's
 * {@link CallerShape#methodMatching}, so the loop and this can never disagree on what a caller's shape is.
 * <p>
 * Note what an emitted class therefore names : {@code tClass} and {@code dClass}, which are the caller's own
 * types and none of core's. {@link GeneratedClassLoader} delegates to this module's loader, so those two have
 * to be visible from there — true on a classpath, and the thing to revisit first if these callers are ever
 * built under an isolating loader.
 * <p>
 * What is <em>not</em> like the from-Glob side : nothing here reads the layout of a Glob. The functions do
 * their own writing through {@link org.globsframework.core.model.MutableGlob}, so there is no GlobType to
 * look at, no CHECKCAST to a generated Glob class and no ClassLoader to borrow from a factory. It works
 * whatever built the Glob it is handed, generated or not, and this class is therefore usable on its own,
 * without {@code globs.builder}.
 * <p>
 * A parser should not name it, though : {@code ToGlobCallerFactory.get()} answers this through
 * {@link AsmCallerWriteGeneratorService} when {@code -Dglobs.caller.toGlob} installs it, and core's looped
 * {@code LoopToGlobCallerFactory} otherwise — same behaviour, so one code path.
 *
 * <pre>
 * interface GlobReader { void read(MutableGlob data, CodedInputStream in); }          // the caller's
 * interface FieldReader { void readField(MutableGlob data, CodedInputStream in); }    // the functions'
 *
 * SortedMap&lt;Integer, FieldReader&gt; functions = new TreeMap&lt;&gt;();
 * // ... one per attribute of the format, keyed by whatever the input answers for it
 * GlobReader reader = ToGlobCallerFactory.get().create("myformat.read", functions, skipUnknown, END,
 *         GlobReader.class, FieldReader.class, MutableGlob.class, CodedInputStream.class);
 * reader.read(type.instantiate(), in);      // CodedInputStream is the KeySource : it drives its own loop
 * </pre>
 *
 * The emitted method is one method, so it is the usual bytecode budget that caps how many entries are
 * worth unrolling : a case is a dozen bytes plus its switch entry, which leaves room for a few thousand, well
 * past the point where the JIT stops inlining any of it.
 * <p>
 * <b>The chunk</b>, on the unrolled caller only. An unrolled call is ~12 bytes (a GETSTATIC, its arguments and
 * the invoke), so past ~27 entries the emitted method is over {@code FreqInlineSize} (325) and C2
 * stops inlining it <em>as a whole</em> — the one method that was supposed to be folded into its caller
 * becomes a call. Emitting the entries in several private static parts of at most {@code chunk} each puts
 * every method back under the threshold : the caller's method is then three invokestatic, and each part
 * inlines on its own. It is a JIT knob and nothing else — same functions, same order, same result — so it is
 * set per process with {@code -Dglobs.caller.toGlob.chunk=<n>} (0, the default, emits one method as before)
 * or per generator with {@link #withChunk(int)}. The chunk is in the digest of the generated name : two chunk
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
    private static final String KEY_SOURCE = CALLER_PKG + "KeySource";
    private static final String CALLER_FACTORY = CALLER_PKG + "ToGlobCallerFactory";
    private static final String OBJECTS_DESC = "[Ljava/lang/Object;";
    private static final String GEN_PACKAGE = "org/globsframework/gen/toglob/";

    // What a generated caller's <clinit> reads, keyed by the name of the class that reads it -- a name
    // GeneratedName has already made unique, so two creations never race over one entry. Same protocol as
    // the PENDING map of the other generators : the entry lives only for the duration of create, so nothing
    // here keeps a function -- nor the ClassLoader of the generated class -- alive. The functions are of the
    // caller's own type, not of one of core's, so they travel as Object[] and the <clinit> casts each one.
    private static final Map<String, Object[]> PENDING = new ConcurrentHashMap<>();

    /**
     * The dispatching shape : {@code while ((next = keySource.nextKey()) != endLoop) switch (next) { … }},
     * each case calling its function through {@code dClass}'s own method.
     */
    @SuppressWarnings("unchecked")
    public <T, D> T create(String name, SortedMap<Integer, D> functions, D fallback, int endLoop,
                           Class<T> tClass, Class<D> dClass, Class<?>... argument) {
        CallerName.check(name);
        CallerShape.checkInterface(tClass);
        // the argument the loop asks what comes next -- core's rule, so the loop and this drive the same one
        int keySourceAt = ToGlobCallerFactory.keySourceIndex(argument);
        EmittedShape shape = shapeOf(tClass, dClass, argument);
        // sorted here rather than trusted : a lookupswitch wants its keys ascending, and the map may have
        // been built with a comparator of its own
        int[] keys = functions.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        Object[] all = new Object[keys.length + (fallback != null ? 1 : 0)];
        for (int i = 0; i < keys.length; i++) {
            all[i] = CallerShape.checked(functions.get(keys[i]), "key " + keys[i]);
        }
        if (fallback != null) {
            all[keys.length] = fallback;
        }
        // the shape is in the digest, not only the name : nothing here is a GlobType, so a parser giving one
        // name per type is what tells two callers apart, and two shapes under one name still have to be two
        // classes -- with names that say so rather than an order of creation
        String callerName = GEN_PACKAGE + GeneratedName.unique("Caller",
                new String[]{name, GeneratedName.simpleName(tClass.getName())},
                shape.identity(name, Arrays.toString(keys), Boolean.toString(fallback != null),
                        Integer.toString(endLoop), Integer.toString(keySourceAt)));
        return (T) generate(callerName, all,
                () -> generateCaller(callerName, shape, keys, fallback != null, endLoop, keySourceAt));
    }

    /** The unrolled shape : the array of functions written out, one call site per element. */
    @SuppressWarnings("unchecked")
    public <T, D> T create(String name, D[] functions, Class<T> tClass, Class<D> dClass,
                           Class<?>... argument) {
        CallerName.check(name);
        CallerShape.checkInterface(tClass);
        EmittedShape shape = shapeOf(tClass, dClass, argument);
        Object[] all = new Object[functions.length];
        for (int i = 0; i < functions.length; i++) {
            all[i] = CallerShape.checked(functions[i], "index " + i);
        }
        // the chunk that will really be emitted, not the one asked for : below it nothing is split, and the
        // bytes -- hence the name -- are those of a caller built without a chunk at all
        int emitted = chunk == 0 || all.length <= chunk ? 0 : chunk;
        String callerName = GEN_PACKAGE + GeneratedName.unique("CallerAll",
                new String[]{name, GeneratedName.simpleName(tClass.getName())},
                shape.identity(name, Integer.toString(all.length), Integer.toString(emitted)));
        return (T) generate(callerName, all,
                () -> generateCallerAll(callerName, shape, all.length, emitted));
    }

    /** Both methods take the same list here : a to-Glob caller passes its arguments straight through. */
    private static EmittedShape shapeOf(Class<?> tClass, Class<?> dClass, Class<?>... argument) {
        // the same rule as the loop's, from core : the shape of a caller is not something to re-decide here
        return EmittedShape.of(tClass, dClass, CallerShape.methodMatching(tClass, argument),
                CallerShape.methodMatching(dClass, argument));
    }

    /**
     * Defines the caller in {@link GeneratedClassLoader} and instantiates it, which is what runs the
     * {@code <clinit>} reading PENDING.
     * <p>
     * The loader delegates to this module's, which is where {@code tClass} and {@code dClass} have to be
     * visible from — see the class comment.
     */
    private static Object generate(String callerName, Object[] functions, Supplier<byte[]> bytes) {
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
    public static Object[] getFunctions(String callerName) {
        Object[] functions = PENDING.get(callerName);
        if (functions == null) {
            throw new IllegalStateException("Nothing registered for generated write caller " + callerName
                                            + " : the generated class was initialized outside of create.");
        }
        return functions;
    }

    /**
     * {@code while ((next = keySource.nextKey()) != endLoop) switch (next) { case k -> fn_i.d(args); }}
     * <p>
     * The endLoop test comes first, so that value never reaches the switch : it may be a key of the map, and
     * ending wins. The switch is a tableswitch when the keys are dense enough to pay for the holes, a
     * lookupswitch otherwise — the same trade javac makes. The key source is one of the arguments, so
     * {@code nextKey} is asked of the slot it sits in and nothing is passed twice.
     */
    static byte[] generateCaller(String callerName, EmittedShape shape, int[] keys, boolean hasFallback,
                                 int endLoop, int keySourceAt) {
        ClassWriter classWriter = newClassWriter(callerName, shape.itf());
        declareFunctions(classWriter, shape, keys.length, hasFallback);
        generateInit(classWriter);
        generateClinit(classWriter, callerName, shape, keys.length, hasFallback);

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL,
                shape.callerMethod().getName(), shape.callerDescriptor(), null, shape.exceptions());
        methodVisitor.visitCode();

        int nextSlot = shape.slotAfterArguments();
        Label top = new Label();
        Label end = new Label();
        Label dflt = new Label();
        Label[] labels = new Label[keys.length];
        Arrays.setAll(labels, i -> new Label());

        methodVisitor.visitLabel(top);
        methodVisitor.visitVarInsn(ALOAD, shape.slotOf(keySourceAt, 1));
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, KEY_SOURCE, "nextKey", "()I", true);
        methodVisitor.visitVarInsn(ISTORE, nextSlot);
        methodVisitor.visitVarInsn(ILOAD, nextSlot);
        pushInt(methodVisitor, endLoop);
        methodVisitor.visitJumpInsn(IF_ICMPEQ, end);

        methodVisitor.visitVarInsn(ILOAD, nextSlot);
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
            emitCall(methodVisitor, callerName, shape, functionName(i), 1);
            methodVisitor.visitJumpInsn(GOTO, top);
        }

        methodVisitor.visitLabel(dflt);
        if (hasFallback) {
            emitCall(methodVisitor, callerName, shape, "fallback", 1);
            methodVisitor.visitJumpInsn(GOTO, top);
        } else {
            // core's, not one of ours : the loop and the generated caller say the same thing for the same key
            methodVisitor.visitVarInsn(ILOAD, nextSlot);
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
     * In one method when {@code chunk} is 0, else in parts of at most {@code chunk} entries that the caller's
     * method invokes in order — same functions, same order, only the method they sit in changes. The parts
     * are {@code private static} because everything they touch is : the functions are static fields, so a
     * part takes the arguments and nothing else.
     */
    static byte[] generateCallerAll(String callerName, EmittedShape shape, int count, int chunk) {
        ClassWriter classWriter = newClassWriter(callerName, shape.itf());
        declareFunctions(classWriter, shape, count, false);
        generateInit(classWriter);
        generateClinit(classWriter, callerName, shape, count, false);

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL,
                shape.callerMethod().getName(), shape.callerDescriptor(), null, shape.exceptions());
        methodVisitor.visitCode();
        if (chunk == 0) {
            for (int i = 0; i < count; i++) {
                emitCall(methodVisitor, callerName, shape, functionName(i), 1);
            }
        } else {
            for (int part = 0; part * chunk < count; part++) {
                shape.loadArguments(methodVisitor, 0, 1);
                methodVisitor.visitMethodInsn(INVOKESTATIC, callerName, partName(part),
                        shape.callerDescriptor(), false);
            }
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        if (chunk != 0) {
            for (int part = 0; part * chunk < count; part++) {
                generatePart(classWriter, callerName, shape, partName(part), part * chunk,
                        Math.min(part * chunk + chunk, count));
            }
        }

        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    /** One part of a chunked unrolled caller : the entries of {@code [from, to)}, in order. */
    private static void generatePart(ClassWriter classWriter, String callerName, EmittedShape shape, String part,
                                     int from, int to) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PRIVATE | ACC_STATIC, part,
                shape.callerDescriptor(), null, shape.exceptions());
        methodVisitor.visitCode();
        for (int i = from; i < to; i++) {
            emitCall(methodVisitor, callerName, shape, functionName(i), 0);
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

    private static void declareFunctions(ClassWriter classWriter, EmittedShape shape, int count,
                                         boolean hasFallback) {
        for (int i = 0; i < count; i++) {
            classWriter.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, functionName(i), shape.functionDesc(),
                    null, null).visitEnd();
        }
        if (hasFallback) {
            classWriter.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "fallback", shape.functionDesc(),
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
    private static void generateClinit(ClassWriter classWriter, String callerName, EmittedShape shape, int count,
                                       boolean hasFallback) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        methodVisitor.visitCode();
        // its own name, which is the key it was registered under : a constant the class already implies, so
        // the bytes stay a pure function of what the name digests
        methodVisitor.visitLdcInsn(callerName);
        methodVisitor.visitMethodInsn(INVOKESTATIC, GENERATOR, "getFunctions",
                "(Ljava/lang/String;)" + OBJECTS_DESC, false);
        methodVisitor.visitVarInsn(ASTORE, 0);
        for (int i = 0; i < count; i++) {
            storeFunction(methodVisitor, callerName, shape, i, functionName(i));
        }
        if (hasFallback) {
            storeFunction(methodVisitor, callerName, shape, count, "fallback");
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    /** The functions travel as Object[], being of the caller's own type : one CHECKCAST per static. */
    private static void storeFunction(MethodVisitor methodVisitor, String callerName, EmittedShape shape, int at,
                                      String field) {
        methodVisitor.visitVarInsn(ALOAD, 0);
        pushInt(methodVisitor, at);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitTypeInsn(CHECKCAST, shape.function());
        methodVisitor.visitFieldInsn(PUTSTATIC, callerName, field, shape.functionDesc());
    }

    /** {@code fn.d(args)} — the function's own method, the arguments passed straight through. */
    private static void emitCall(MethodVisitor methodVisitor, String callerName, EmittedShape shape, String function,
                                 int firstSlot) {
        methodVisitor.visitFieldInsn(GETSTATIC, callerName, function, shape.functionDesc());
        shape.loadArguments(methodVisitor, 0, firstSlot);
        shape.invokeFunction(methodVisitor);
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

    private static String partName(int index) {
        return "part_" + index;
    }

    /** Any int : a key or an endLoop value can be negative or large. */
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
