package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.KeySource;
import org.globsframework.core.model.caller.LoopToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerService;
import org.globsframework.core.model.caller.ToGlobCaller;
import org.globsframework.core.model.caller.ToGlobCallerAll;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobFunction;
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
 * Nothing here sets {@code globs.builder} : the to-Glob side never reads the layout of a Glob, so it works over
 * whatever the type's factory builds — core's DefaultGlob here — and that is part of the contract.
 */
public class GeneratedToGlobCallerTest {

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
        ToGlobCallerService.Builder.reset();
    }

    /** ctx1 is the trace, so what it collects is the proof the three contexts were forwarded. */
    private ToGlobFunction<List<String>, String, String> record(String label) {
        return (glob, trace, ctx2, ctx3) -> trace.add(label + "/" + ctx2 + "/" + ctx3);
    }

    /** Answers the script, then {@code endLoop} for ever — a parser that ran out of input. */
    private KeySource script(int endLoop, int... calls) {
        return new KeySource() {
            int next = 0;

            public int nextKey() {
                return next < calls.length ? calls[next++] : endLoop;
            }
        };
    }

    private SortedMap<Integer, ToGlobFunction<List<String>, String, String>> functions(int... keys) {
        SortedMap<Integer, ToGlobFunction<List<String>, String, String>> functions = new TreeMap<>();
        for (int key : keys) {
            functions.put(key, record("fn" + key));
        }
        return functions;
    }

    private List<String> call(ToGlobCaller<List<String>, String, String> caller, KeySource keySource) {
        List<String> trace = new ArrayList<>();
        caller.call(keySource, type.instantiate(), trace, "c2", "c3");
        return trace;
    }

    /** Dense keys : a tableswitch, holes included. */
    @Test
    public void callsWhatTheCallAtAsksForInOrder() {
        ToGlobCaller<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions(0, 1, 2, 3), record("fallback"), -1);

        Assertions.assertEquals(List.of("fn2/c2/c3", "fn0/c2/c3", "fn2/c2/c3", "fn3/c2/c3", "fn1/c2/c3"),
                call(caller, script(-1, 2, 0, 2, 3, 1)));
    }

    /** Sparse and negative keys : a lookupswitch, whose keys the switch wants ascending. */
    @Test
    public void sparseAndNegativeKeys() {
        ToGlobCaller<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions(-40000, -3, 7, 100000), record("fallback"), -1);

        Assertions.assertEquals(List.of("fn100000/c2/c3", "fn-3/c2/c3", "fn-40000/c2/c3", "fn7/c2/c3"),
                call(caller, script(-1, 100000, -3, -40000, 7)));
    }

    /** The keys are sorted by the generator, not taken as the map iterates them. */
    @Test
    public void aMapWithItsOwnComparatorIsStillGeneratedRight() {
        SortedMap<Integer, ToGlobFunction<List<String>, String, String>> functions =
                new TreeMap<>(Comparator.reverseOrder());
        for (int key : new int[]{1, 5, 9, 12}) {
            functions.put(key, record("fn" + key));
        }
        ToGlobCaller<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions, record("fallback"), -1);

        Assertions.assertEquals(List.of("fn9/c2/c3", "fn1/c2/c3", "fn12/c2/c3", "fn5/c2/c3"),
                call(caller, script(-1, 9, 1, 12, 5)));
    }

    @Test
    public void anUnknownKeyGoesToTheFallback() {
        ToGlobCaller<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions(1, 2), record("fallback"), -1);

        Assertions.assertEquals(List.of("fallback/c2/c3", "fn1/c2/c3", "fallback/c2/c3"),
                call(caller, script(-1, 17, 1, -2)));
    }

    @Test
    public void anUnknownKeyWithoutAFallbackThrowsAndSaysWhich() {
        ToGlobCaller<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions(1, 2), null, -1);

        Assertions.assertEquals(List.of("fn2/c2/c3"), call(caller, script(-1, 2)));
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> call(caller, script(-1, 2, 17)));
        Assertions.assertTrue(exception.getMessage().contains("17"), exception.getMessage());
    }

    /** endLoop is tested before the switch : it ends the pass even when it is also a key. */
    @Test
    public void anEndLoopOfItsOwnShadowsTheKeyItEquals() {
        ToGlobCaller<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions(1, 2, 3), record("fallback"), 3);

        Assertions.assertEquals(List.of("fn1/c2/c3", "fn2/c2/c3"), call(caller, script(3, 1, 2, 3, 1)));
    }

    @Test
    public void noFunctionAtAllIsALoopThatOnlyWaitsForTheEnd() {
        ToGlobCaller<List<String>, String, String> caller = AsmCallerWriteGenerator.INSTANCE
                .create("test", Collections.emptySortedMap(), record("fallback"), 0);

        Assertions.assertEquals(List.of("fallback/c2/c3", "fallback/c2/c3"), call(caller, script(0, 4, 9)));
    }

    /** The functions get the Glob and write into it — the point of the whole thing. */
    @Test
    public void theFunctionsWriteIntoTheGlobTheyAreHanded() {
        SortedMap<Integer, ToGlobFunction<List<String>, String, String>> functions = new TreeMap<>();
        functions.put(0, (glob, trace, ctx2, ctx3) -> glob.set(name, "n" + trace.size()));
        functions.put(1, (glob, trace, ctx2, ctx3) -> glob.set(count, 12));
        ToGlobCaller<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions, null, -1);

        MutableGlob glob = type.instantiate();
        caller.call(script(-1, 1, 0), glob, new ArrayList<>(), "c2", "c3");

        Assertions.assertEquals("n0", glob.get(name));
        Assertions.assertEquals(12, glob.get(count).intValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void writeAllCallsEveryFunctionOnceInOrder() {
        ToGlobCallerAll<List<String>, String, String> caller = AsmCallerWriteGenerator.INSTANCE
                .create("test", new ToGlobFunction[]{record("a"), record("b"), record("c")});

        List<String> trace = new ArrayList<>();
        caller.call(type.instantiate(), trace, "c2", "c3");

        Assertions.assertEquals(List.of("a/c2/c3", "b/c2/c3", "c/c2/c3"), trace);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void writeAllOfNothingIsAnEmptyCall() {
        ToGlobCallerAll<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", new ToGlobFunction[0]);

        List<String> trace = new ArrayList<>();
        caller.call(type.instantiate(), trace, "c2", "c3");

        Assertions.assertTrue(trace.isEmpty());
    }

    /**
     * What the whole design rests on : one class per create — two callers over the same keys do not share
     * their call sites — holding the functions in public static final fields the JIT can fold.
     */
    @Test
    public void aClassPerCreateHoldingItsOwnFunctionsInStaticFinals() throws Exception {
        ToGlobCaller<List<String>, String, String> first =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions(1, 2), record("fallback"), -1);
        ToGlobCaller<List<String>, String, String> second =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions(1, 2), record("fallback"), -1);

        Assertions.assertNotSame(first.getClass(), second.getClass());
        for (String field : new String[]{"fn_0", "fn_1", "fallback"}) {
            int modifiers = first.getClass().getDeclaredField(field).getModifiers();
            Assertions.assertTrue(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                                  && Modifier.isPublic(modifiers), field + " : " + modifiers);
        }
        Assertions.assertThrows(NoSuchFieldException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create("test", functions(1, 2), null, -1)
                        .getClass().getDeclaredField("fallback"));
    }

    @Test
    public void aMissingFunctionIsRefusedAtGenerationTime() {
        SortedMap<Integer, ToGlobFunction<List<String>, String, String>> functions = functions(1, 2);
        functions.put(3, null);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create("test", functions, null, -1));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create("test", new ToGlobFunction[]{record("a"), null}));
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
                    call(LoopToGlobCallerFactory.INSTANCE.create("test", functions(keys), record("fallback"), -1),
                            script(-1, script)),
                    call(AsmCallerWriteGenerator.INSTANCE.create("test", functions(keys), record("fallback"), -1),
                            script(-1, script)));

            ToGlobCaller<List<String>, String, String> looped =
                    LoopToGlobCallerFactory.INSTANCE.create("test", functions(keys), null, -1);
            ToGlobCaller<List<String>, String, String> generated =
                    AsmCallerWriteGenerator.INSTANCE.create("test", functions(keys), null, -1);
            Assertions.assertEquals(
                    Assertions.assertThrows(IllegalStateException.class,
                            () -> call(looped, script(-1, script))).getMessage(),
                    Assertions.assertThrows(IllegalStateException.class,
                            () -> call(generated, script(-1, script))).getMessage());
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
        ToGlobCaller<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create("test", functions(keys), record("fallback"), Integer.MIN_VALUE);

        List<String> expected = new ArrayList<>();
        for (int key : keys) {
            expected.add("fn" + key + "/c2/c3");
        }
        Assertions.assertEquals(expected, call(caller, script(Integer.MIN_VALUE, keys)));
    }
}
