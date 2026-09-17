package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.KeySource;
import org.globsframework.core.model.caller.LoopToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerService;
import org.globsframework.model.generator.AsmCallerWriteGenerator;
import org.globsframework.model.generator.AsmCallerWriteGeneratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The to-Glob callers : the switch a {@link KeySource} drives, and the unrolled one.
 * <p>
 * Everything is over the test's own two interfaces, which is the only shape there is on this side : core
 * names {@link KeySource} and nothing else, and the emitted class implements what the caller asked for.
 * <p>
 * Nothing here sets {@code globs.builder} : the to-Glob side never reads the layout of a Glob, so it works over
 * whatever the type's factory builds — core's DefaultGlob here — and that is part of the contract.
 */
public class GeneratedToGlobCallerTest {

    /**
     * The input : both what says the next key and what the functions read from, the way a parser's stream is.
     * It carries the trace, so what it collects is the proof the arguments reached the functions.
     */
    public static class Script implements KeySource {
        private final int endLoop;
        private final int[] calls;
        final List<String> trace = new ArrayList<>();
        private int next;

        /** Answers the script, then {@code endLoop} for ever — a parser that ran out of input. */
        Script(int endLoop, int... calls) {
            this.endLoop = endLoop;
            this.calls = calls;
        }

        public int nextKey() {
            return next < calls.length ? calls[next++] : endLoop;
        }
    }

    /** The caller's interface : what the code holding the loop wants to call. */
    public interface GlobReader {
        void read(MutableGlob data, Script input, String ctx);
    }

    /** The function's, written independently — note the method is not named like the caller's. */
    public interface FieldReader {
        void readField(MutableGlob data, Script input, String ctx);
    }

    /** A long and a double take two slots each : the key of the loop has to land past them. */
    public interface WideReader {
        void read(MutableGlob data, long offset, Script input, double scale, String ctx);
    }

    public interface WideFieldReader {
        void readAt(MutableGlob data, long offset, Script input, double scale, String ctx);
    }

    private static final Class<?>[] ARGS = {MutableGlob.class, Script.class, String.class};

    private final GlobType type;
    private final StringField name;
    private final IntegerField count;

    public GeneratedToGlobCallerTest() {
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("WriteTarget");
        name = builder.declareStringField("name");
        count = builder.declareIntegerField("count");
        type = builder.build();
    }

    /** the service is cached, so a test that sets the property has to put it back for the others */
    @AfterEach
    public void tearDown() {
        System.clearProperty("globs.caller.toGlob");
        System.clearProperty(AsmCallerWriteGenerator.CHUNK_PROPERTY);
        ToGlobCallerService.Builder.reset();
    }

    private FieldReader record(String label) {
        return (glob, input, ctx) -> input.trace.add(label + "/" + ctx);
    }

    private SortedMap<Integer, FieldReader> functions(int... keys) {
        SortedMap<Integer, FieldReader> functions = new TreeMap<>();
        for (int key : keys) {
            functions.put(key, record("fn" + key));
        }
        return functions;
    }

    private GlobReader caller(ToGlobCallerFactory factory, SortedMap<Integer, FieldReader> functions,
                              FieldReader fallback, int endLoop) {
        return factory.create("test", functions, fallback, endLoop, GlobReader.class, FieldReader.class,
                ARGS);
    }

    private GlobReader caller(SortedMap<Integer, FieldReader> functions, FieldReader fallback, int endLoop) {
        return caller(AsmCallerWriteGenerator.INSTANCE, functions, fallback, endLoop);
    }

    private List<String> call(GlobReader caller, Script input) {
        caller.read(type.instantiate(), input, "ctx");
        return input.trace;
    }

    /** Dense keys : a tableswitch, holes included. */
    @Test
    public void callsWhatTheKeySourceAsksForInOrder() {
        GlobReader caller = caller(functions(0, 1, 2, 3), record("fallback"), -1);

        Assertions.assertEquals(List.of("fn2/ctx", "fn0/ctx", "fn2/ctx", "fn3/ctx", "fn1/ctx"),
                call(caller, new Script(-1, 2, 0, 2, 3, 1)));
    }

    /** Sparse and negative keys : a lookupswitch, whose keys the switch wants ascending. */
    @Test
    public void sparseAndNegativeKeys() {
        GlobReader caller = caller(functions(-40000, -3, 7, 100000), record("fallback"), -1);

        Assertions.assertEquals(List.of("fn100000/ctx", "fn-3/ctx", "fn-40000/ctx", "fn7/ctx"),
                call(caller, new Script(-1, 100000, -3, -40000, 7)));
    }

    /** The keys are sorted by the generator, not taken as the map iterates them. */
    @Test
    public void aMapWithItsOwnComparatorIsStillGeneratedRight() {
        SortedMap<Integer, FieldReader> functions = new TreeMap<>(Comparator.reverseOrder());
        for (int key : new int[]{1, 5, 9, 12}) {
            functions.put(key, record("fn" + key));
        }

        Assertions.assertEquals(List.of("fn9/ctx", "fn1/ctx", "fn12/ctx", "fn5/ctx"),
                call(caller(functions, record("fallback"), -1), new Script(-1, 9, 1, 12, 5)));
    }

    @Test
    public void anUnknownKeyGoesToTheFallback() {
        Assertions.assertEquals(List.of("fallback/ctx", "fn1/ctx", "fallback/ctx"),
                call(caller(functions(1, 2), record("fallback"), -1), new Script(-1, 17, 1, -2)));
    }

    @Test
    public void anUnknownKeyWithoutAFallbackThrowsAndSaysWhich() {
        GlobReader caller = caller(functions(1, 2), null, -1);

        Assertions.assertEquals(List.of("fn2/ctx"), call(caller, new Script(-1, 2)));
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> call(caller, new Script(-1, 2, 17)));
        Assertions.assertTrue(exception.getMessage().contains("17"), exception.getMessage());
    }

    /** endLoop is tested before the switch : it ends the pass even when it is also a key. */
    @Test
    public void anEndLoopOfItsOwnShadowsTheKeyItEquals() {
        Assertions.assertEquals(List.of("fn1/ctx", "fn2/ctx"),
                call(caller(functions(1, 2, 3), record("fallback"), 3), new Script(3, 1, 2, 3, 1)));
    }

    @Test
    public void noFunctionAtAllIsALoopThatOnlyWaitsForTheEnd() {
        Assertions.assertEquals(List.of("fallback/ctx", "fallback/ctx"),
                call(caller(Collections.emptySortedMap(), record("fallback"), 0), new Script(0, 4, 9)));
    }

    /** The functions get the Glob and write into it — the point of the whole thing. */
    @Test
    public void theFunctionsWriteIntoTheGlobTheyAreHanded() {
        SortedMap<Integer, FieldReader> functions = new TreeMap<>();
        functions.put(0, (glob, input, ctx) -> glob.set(name, "n" + input.trace.size()));
        functions.put(1, (glob, input, ctx) -> glob.set(count, 12));

        MutableGlob glob = type.instantiate();
        caller(functions, null, -1).read(glob, new Script(-1, 1, 0), "ctx");

        Assertions.assertEquals("n0", glob.get(name));
        Assertions.assertEquals(12, glob.get(count).intValue());
    }

    /**
     * The key of the loop is a local of its own, and it has to come after the arguments : with a long and a
     * double among them, the naive "one slot per argument" puts it on top of one of them.
     */
    @Test
    public void wideArgumentsLeaveRoomForTheKeyOfTheLoop() {
        SortedMap<Integer, WideFieldReader> functions = new TreeMap<>();
        functions.put(1, (data, offset, input, scale, ctx) ->
                input.trace.add("one:" + offset + "/" + scale + "/" + ctx));
        functions.put(2, (data, offset, input, scale, ctx) ->
                input.trace.add("two:" + offset + "/" + scale + "/" + ctx));
        WideReader caller = AsmCallerWriteGenerator.INSTANCE.create("test.wide", functions, null,
                Integer.MIN_VALUE, WideReader.class, WideFieldReader.class,
                MutableGlob.class, long.class, Script.class, double.class, String.class);

        Script input = new Script(Integer.MIN_VALUE, 2, 1, 2);
        caller.read(type.instantiate(), Long.MAX_VALUE, input, 0.5d, "ctx");

        Assertions.assertEquals(List.of("two:" + Long.MAX_VALUE + "/0.5/ctx",
                "one:" + Long.MAX_VALUE + "/0.5/ctx", "two:" + Long.MAX_VALUE + "/0.5/ctx"), input.trace);
    }

    @Test
    public void readAllCallsEveryFunctionOnceInOrder() {
        GlobReader caller = AsmCallerWriteGenerator.INSTANCE.create("test",
                new FieldReader[]{record("a"), record("b"), record("c")},
                GlobReader.class, FieldReader.class, ARGS);

        Assertions.assertEquals(List.of("a/ctx", "b/ctx", "c/ctx"), call(caller, new Script(-1)));
    }

    @Test
    public void readAllOfNothingIsAnEmptyCall() {
        GlobReader caller = AsmCallerWriteGenerator.INSTANCE.create("test", new FieldReader[0],
                GlobReader.class, FieldReader.class, ARGS);

        Assertions.assertTrue(call(caller, new Script(-1)).isEmpty());
    }

    private FieldReader[] records(int count) {
        FieldReader[] functions = new FieldReader[count];
        for (int i = 0; i < count; i++) {
            functions[i] = record("f" + i);
        }
        return functions;
    }

    private GlobReader all(AsmCallerWriteGenerator generator, String name, FieldReader... functions) {
        return generator.create(name, functions, GlobReader.class, FieldReader.class, ARGS);
    }

    private List<String> expected(int count) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            labels.add("f" + i + "/ctx");
        }
        return labels;
    }

    /**
     * A chunk only moves the entries into several emitted methods : same functions, same order, same result —
     * including the last part, which is the only one the count does not fill.
     */
    @Test
    public void aChunkedReadAllCallsEveryFunctionOnceInOrder() {
        for (int chunk : new int[]{1, 2, 4, 7, 10}) {
            Assertions.assertEquals(expected(10),
                    call(all(AsmCallerWriteGenerator.withChunk(chunk), "test", records(10)), new Script(-1)),
                    "chunk " + chunk);
        }
    }

    /** The parts are an implementation detail of the class : private, static, and there. */
    @Test
    public void aChunkEmitsOnePrivateStaticMethodPerPart() {
        Class<?> chunked = all(AsmCallerWriteGenerator.withChunk(4), "test", records(10)).getClass();

        for (String part : new String[]{"part_0", "part_1", "part_2"}) {
            int modifiers = Assertions.assertDoesNotThrow(
                    () -> chunked.getDeclaredMethod(part, ARGS)).getModifiers();
            Assertions.assertTrue(Modifier.isStatic(modifiers) && Modifier.isPrivate(modifiers),
                    part + " : " + modifiers);
        }
        Assertions.assertThrows(NoSuchMethodException.class,
                () -> chunked.getDeclaredMethod("part_3", ARGS));
        Assertions.assertThrows(NoSuchMethodException.class,
                () -> all(AsmCallerWriteGenerator.INSTANCE, "test", records(10)).getClass()
                        .getDeclaredMethod("part_0", ARGS));
    }

    /** Nothing to split is not a different class : below the chunk, the bytes are those of no chunk at all. */
    @Test
    public void aChunkOverTheCountSplitsNothing() {
        Class<?> asked = all(AsmCallerWriteGenerator.withChunk(10), "chunkOverCount", records(4)).getClass();
        Class<?> none = all(AsmCallerWriteGenerator.INSTANCE, "chunkOverCount", records(4)).getClass();

        // same name up to the suffix that keeps two creations apart, i.e. the same digest : the same bytes
        Assertions.assertEquals(asked.getName().replaceAll("_\\d+$", ""),
                none.getName().replaceAll("_\\d+$", ""));
        Assertions.assertThrows(NoSuchMethodException.class, () -> asked.getDeclaredMethod("part_0", ARGS));
    }

    /** Two chunks are two sets of bytes, so they have to be two names. */
    @Test
    public void twoChunksAreTwoClasses() {
        String four = all(AsmCallerWriteGenerator.withChunk(4), "chunkNaming", records(10))
                .getClass().getName().replaceAll("_\\d+$", "");
        String five = all(AsmCallerWriteGenerator.withChunk(5), "chunkNaming", records(10))
                .getClass().getName().replaceAll("_\\d+$", "");

        Assertions.assertNotEquals(four, five);
    }

    @Test
    public void theChunkIsReadFromTheProperty() {
        Assertions.assertSame(AsmCallerWriteGenerator.INSTANCE, AsmCallerWriteGenerator.fromProperty());

        System.setProperty(AsmCallerWriteGenerator.CHUNK_PROPERTY, "4");
        Assertions.assertSame(AsmCallerWriteGenerator.withChunk(4), AsmCallerWriteGenerator.fromProperty());
        Assertions.assertSame(AsmCallerWriteGenerator.withChunk(4),
                new AsmCallerWriteGeneratorService().factory());

        System.setProperty(AsmCallerWriteGenerator.CHUNK_PROPERTY, "not a number");
        Assertions.assertThrows(IllegalArgumentException.class, AsmCallerWriteGenerator::fromProperty);
    }

    @Test
    public void aNegativeChunkIsRefused() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> AsmCallerWriteGenerator.withChunk(-1));
    }

    /**
     * What the whole design rests on : one class per create — two callers over the same keys do not share
     * their call sites — holding the functions in public static final fields of the caller's own type, which
     * the JIT can fold.
     */
    @Test
    public void aClassPerCreateHoldingItsOwnFunctionsInStaticFinals() throws Exception {
        GlobReader first = caller(functions(1, 2), record("fallback"), -1);
        GlobReader second = caller(functions(1, 2), record("fallback"), -1);

        Assertions.assertNotSame(first.getClass(), second.getClass());
        for (String field : new String[]{"fn_0", "fn_1", "fallback"}) {
            int modifiers = first.getClass().getDeclaredField(field).getModifiers();
            Assertions.assertTrue(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                                  && Modifier.isPublic(modifiers), field + " : " + modifiers);
            Assertions.assertEquals(FieldReader.class, first.getClass().getDeclaredField(field).getType());
        }
        // the emitted method is the interface's own, with the interface's own descriptor
        Assertions.assertEquals(void.class,
                first.getClass().getDeclaredMethod("read", ARGS).getReturnType());
        Assertions.assertThrows(NoSuchFieldException.class,
                () -> caller(functions(1, 2), null, -1).getClass().getDeclaredField("fallback"));
    }

    @Test
    public void theRefusalsAreTheSameAsTheFallbackOnes() {
        SortedMap<Integer, FieldReader> functions = functions(1, 2);
        functions.put(3, null);
        Assertions.assertThrows(IllegalArgumentException.class, () -> caller(functions, null, -1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> AsmCallerWriteGenerator.INSTANCE
                .create("test", new FieldReader[]{record("a"), null}, GlobReader.class, FieldReader.class,
                        ARGS));
        // what is implemented has to be an interface, and a name is not optional
        Assertions.assertThrows(IllegalArgumentException.class, () -> AsmCallerWriteGenerator.INSTANCE
                .create("test", functions(1), null, -1, String.class, FieldReader.class, ARGS));
        Assertions.assertThrows(IllegalArgumentException.class, () -> AsmCallerWriteGenerator.INSTANCE
                .create("  ", functions(1), null, -1, GlobReader.class, FieldReader.class, ARGS));
    }

    /** No KeySource among the arguments : there is nothing to ask what comes next, so it is refused. */
    @Test
    public void aDispatchingCallerWithoutAKeySourceIsRefused() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> AsmCallerWriteGenerator.INSTANCE
                .create("test", new TreeMap<Integer, Runnable>(), null, -1, Runnable.class, Runnable.class));
    }

    /**
     * The extension point : {@code -Dglobs.caller.toGlob} is what a parser goes through, and it is what makes
     * this module reachable without being named. Unset, core keeps answering its loop.
     */
    @Test
    public void theServiceIsWhatGetAnswersOnceInstalled() {
        Assertions.assertSame(LoopToGlobCallerFactory.INSTANCE, ToGlobCallerFactory.get());

        System.setProperty("globs.caller.toGlob", AsmCallerWriteGeneratorService.class.getName());
        ToGlobCallerService.Builder.reset();
        try {
            Assertions.assertSame(AsmCallerWriteGenerator.INSTANCE, ToGlobCallerFactory.get());
            Assertions.assertSame(AsmCallerWriteGenerator.INSTANCE, ToGlobCallerFactory.generated());
        } finally {
            System.clearProperty("globs.caller.toGlob");
            ToGlobCallerService.Builder.reset();
        }
        Assertions.assertNull(ToGlobCallerFactory.generated());
    }

    /**
     * What lets a parser keep one code path : the loop and the generated switch are the same pass — same
     * order, same fallback, same end of loop, same refusal.
     */
    @Test
    public void theLoopedCallerAndTheGeneratedOneAgree() {
        int[] script = {2, 0, 17, 2, -3, 100000, 0, -1000};
        for (int[] keys : new int[][]{{-3, 0, 1, 2}, {-3, 0, 2, 100000}}) {
            Assertions.assertEquals(
                    call(caller(LoopToGlobCallerFactory.INSTANCE, functions(keys), record("fallback"), -1),
                            new Script(-1, script)),
                    call(caller(functions(keys), record("fallback"), -1), new Script(-1, script)));

            GlobReader looped = caller(LoopToGlobCallerFactory.INSTANCE, functions(keys), null, -1);
            GlobReader generated = caller(functions(keys), null, -1);
            Assertions.assertEquals(
                    Assertions.assertThrows(IllegalStateException.class,
                            () -> call(looped, new Script(-1, script))).getMessage(),
                    Assertions.assertThrows(IllegalStateException.class,
                            () -> call(generated, new Script(-1, script))).getMessage());
        }
    }

    /** Enough keys that the switch is what dispatches, both shapes, every key reaching its own function. */
    @Test
    public void everyKeyOfALargeSwitchReachesItsOwnFunction() {
        checkEachKey(dense(200));
        checkEachKey(spread(200));
    }

    private int[] dense(int count) {
        int[] keys = new int[count];
        for (int i = 0; i < count; i++) {
            keys[i] = i - count / 2;
        }
        return keys;
    }

    private int[] spread(int count) {
        int[] keys = new int[count];
        for (int i = 0; i < count; i++) {
            keys[i] = i * 977 - 1000;
        }
        return keys;
    }

    private void checkEachKey(int[] keys) {
        GlobReader caller = caller(functions(keys), record("fallback"), Integer.MIN_VALUE);

        List<String> expected = new ArrayList<>();
        for (int key : keys) {
            expected.add("fn" + key + "/ctx");
        }
        Assertions.assertEquals(expected, call(caller, new Script(Integer.MIN_VALUE, keys)));
    }
}
