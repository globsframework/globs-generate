package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.generate.write.CallAtWrite;
import org.globsframework.core.model.generate.write.DefaultFunctionCallerWrite;
import org.globsframework.core.model.generate.write.GenerateCallerWriteService;
import org.globsframework.core.model.generate.write.GeneratedCallerWrite;
import org.globsframework.core.model.generate.write.GeneratedCallerWriteAll;
import org.globsframework.core.model.generate.write.GeneratedFunctionCallerWrite;
import org.globsframework.core.model.generate.write.MutableFunctionWrite;
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
 * The write callers : the switch a {@link CallAtWrite} drives, and the unrolled one.
 * <p>
 * Nothing here sets {@code globs.builder} : the write side never reads the layout of a Glob, so it works over
 * whatever the type's factory builds — core's DefaultGlob here — and that is part of the contract.
 */
public class GeneratedCallerWriteTest {

    private final GlobType type;
    private final StringField name;
    private final IntegerField count;

    public GeneratedCallerWriteTest() {
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("WriteTarget");
        name = builder.declareStringField("name");
        count = builder.declareIntegerField("count");
        type = builder.build();
    }

    /** the service is cached, so a test that sets the property has to put it back for the others */
    @AfterEach
    public void tearDown() {
        System.clearProperty("globs.callerWrite");
        GenerateCallerWriteService.Builder.reset();
    }

    /** ctx1 is the trace, so what it collects is the proof the three contexts were forwarded. */
    private MutableFunctionWrite<List<String>, String, String> record(String label) {
        return (glob, trace, ctx2, ctx3) -> trace.add(label + "/" + ctx2 + "/" + ctx3);
    }

    /** Answers the script, then {@code endLoop} for ever — a parser that ran out of input. */
    private CallAtWrite script(int endLoop, int... calls) {
        return new CallAtWrite() {
            int next = 0;

            public int getNextToCall() {
                return next < calls.length ? calls[next++] : endLoop;
            }
        };
    }

    private SortedMap<Integer, MutableFunctionWrite<List<String>, String, String>> functions(int... keys) {
        SortedMap<Integer, MutableFunctionWrite<List<String>, String, String>> functions = new TreeMap<>();
        for (int key : keys) {
            functions.put(key, record("fn" + key));
        }
        return functions;
    }

    private List<String> call(GeneratedCallerWrite<List<String>, String, String> caller, CallAtWrite callAt) {
        List<String> trace = new ArrayList<>();
        caller.call(callAt, type.instantiate(), trace, "c2", "c3");
        return trace;
    }

    /** Dense keys : a tableswitch, holes included. */
    @Test
    public void callsWhatTheCallAtAsksForInOrder() {
        GeneratedCallerWrite<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(functions(0, 1, 2, 3), record("fallback"), -1);

        Assertions.assertEquals(List.of("fn2/c2/c3", "fn0/c2/c3", "fn2/c2/c3", "fn3/c2/c3", "fn1/c2/c3"),
                call(caller, script(-1, 2, 0, 2, 3, 1)));
    }

    /** Sparse and negative keys : a lookupswitch, whose keys the switch wants ascending. */
    @Test
    public void sparseAndNegativeKeys() {
        GeneratedCallerWrite<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(functions(-40000, -3, 7, 100000), record("fallback"), -1);

        Assertions.assertEquals(List.of("fn100000/c2/c3", "fn-3/c2/c3", "fn-40000/c2/c3", "fn7/c2/c3"),
                call(caller, script(-1, 100000, -3, -40000, 7)));
    }

    /** The keys are sorted by the generator, not taken as the map iterates them. */
    @Test
    public void aMapWithItsOwnComparatorIsStillGeneratedRight() {
        SortedMap<Integer, MutableFunctionWrite<List<String>, String, String>> functions =
                new TreeMap<>(Comparator.reverseOrder());
        for (int key : new int[]{1, 5, 9, 12}) {
            functions.put(key, record("fn" + key));
        }
        GeneratedCallerWrite<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(functions, record("fallback"), -1);

        Assertions.assertEquals(List.of("fn9/c2/c3", "fn1/c2/c3", "fn12/c2/c3", "fn5/c2/c3"),
                call(caller, script(-1, 9, 1, 12, 5)));
    }

    @Test
    public void anUnknownKeyGoesToTheFallback() {
        GeneratedCallerWrite<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(functions(1, 2), record("fallback"), -1);

        Assertions.assertEquals(List.of("fallback/c2/c3", "fn1/c2/c3", "fallback/c2/c3"),
                call(caller, script(-1, 17, 1, -2)));
    }

    @Test
    public void anUnknownKeyWithoutAFallbackThrowsAndSaysWhich() {
        GeneratedCallerWrite<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(functions(1, 2), null, -1);

        Assertions.assertEquals(List.of("fn2/c2/c3"), call(caller, script(-1, 2)));
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> call(caller, script(-1, 2, 17)));
        Assertions.assertTrue(exception.getMessage().contains("17"), exception.getMessage());
    }

    /** endLoop is tested before the switch : it ends the pass even when it is also a key. */
    @Test
    public void anEndLoopOfItsOwnShadowsTheKeyItEquals() {
        GeneratedCallerWrite<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(functions(1, 2, 3), record("fallback"), 3);

        Assertions.assertEquals(List.of("fn1/c2/c3", "fn2/c2/c3"), call(caller, script(3, 1, 2, 3, 1)));
    }

    @Test
    public void noFunctionAtAllIsALoopThatOnlyWaitsForTheEnd() {
        GeneratedCallerWrite<List<String>, String, String> caller = AsmCallerWriteGenerator.INSTANCE
                .create(Collections.emptySortedMap(), record("fallback"), 0);

        Assertions.assertEquals(List.of("fallback/c2/c3", "fallback/c2/c3"), call(caller, script(0, 4, 9)));
    }

    /** The functions get the Glob and write into it — the point of the whole thing. */
    @Test
    public void theFunctionsWriteIntoTheGlobTheyAreHanded() {
        SortedMap<Integer, MutableFunctionWrite<List<String>, String, String>> functions = new TreeMap<>();
        functions.put(0, (glob, trace, ctx2, ctx3) -> glob.set(name, "n" + trace.size()));
        functions.put(1, (glob, trace, ctx2, ctx3) -> glob.set(count, 12));
        GeneratedCallerWrite<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(functions, null, -1);

        MutableGlob glob = type.instantiate();
        caller.call(script(-1, 1, 0), glob, new ArrayList<>(), "c2", "c3");

        Assertions.assertEquals("n0", glob.get(name));
        Assertions.assertEquals(12, glob.get(count).intValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void writeAllCallsEveryFunctionOnceInOrder() {
        GeneratedCallerWriteAll<List<String>, String, String> caller = AsmCallerWriteGenerator.INSTANCE
                .create(new MutableFunctionWrite[]{record("a"), record("b"), record("c")});

        List<String> trace = new ArrayList<>();
        caller.call(type.instantiate(), trace, "c2", "c3");

        Assertions.assertEquals(List.of("a/c2/c3", "b/c2/c3", "c/c2/c3"), trace);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void writeAllOfNothingIsAnEmptyCall() {
        GeneratedCallerWriteAll<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(new MutableFunctionWrite[0]);

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
        GeneratedCallerWrite<List<String>, String, String> first =
                AsmCallerWriteGenerator.INSTANCE.create(functions(1, 2), record("fallback"), -1);
        GeneratedCallerWrite<List<String>, String, String> second =
                AsmCallerWriteGenerator.INSTANCE.create(functions(1, 2), record("fallback"), -1);

        Assertions.assertNotSame(first.getClass(), second.getClass());
        for (String field : new String[]{"fn_0", "fn_1", "fallback"}) {
            int modifiers = first.getClass().getDeclaredField(field).getModifiers();
            Assertions.assertTrue(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                                  && Modifier.isPublic(modifiers), field + " : " + modifiers);
        }
        Assertions.assertThrows(NoSuchFieldException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create(functions(1, 2), null, -1)
                        .getClass().getDeclaredField("fallback"));
    }

    @Test
    public void aMissingFunctionIsRefusedAtGenerationTime() {
        SortedMap<Integer, MutableFunctionWrite<List<String>, String, String>> functions = functions(1, 2);
        functions.put(3, null);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create(functions, null, -1));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create(new MutableFunctionWrite[]{record("a"), null}));
    }

    /**
     * The extension point : {@code -Dglobs.callerWrite} is what a parser goes through, and it is what makes
     * this module reachable without being named. Unset, core keeps answering its loop.
     */
    @Test
    public void theServiceIsWhatGetAnswersOnceInstalled() {
        Assertions.assertSame(DefaultFunctionCallerWrite.INSTANCE, GeneratedFunctionCallerWrite.get());

        System.setProperty("globs.callerWrite", AsmCallerWriteGeneratorService.class.getName());
        GenerateCallerWriteService.Builder.reset();
        try {
            Assertions.assertSame(AsmCallerWriteGenerator.INSTANCE, GeneratedFunctionCallerWrite.get());
            Assertions.assertSame(AsmCallerWriteGenerator.INSTANCE, GeneratedFunctionCallerWrite.getGenerated());
        } finally {
            System.clearProperty("globs.callerWrite");
            GenerateCallerWriteService.Builder.reset();
        }
        Assertions.assertNull(GeneratedFunctionCallerWrite.getGenerated());
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
                    call(DefaultFunctionCallerWrite.INSTANCE.create(functions(keys), record("fallback"), -1),
                            script(-1, script)),
                    call(AsmCallerWriteGenerator.INSTANCE.create(functions(keys), record("fallback"), -1),
                            script(-1, script)));

            GeneratedCallerWrite<List<String>, String, String> looped =
                    DefaultFunctionCallerWrite.INSTANCE.create(functions(keys), null, -1);
            GeneratedCallerWrite<List<String>, String, String> generated =
                    AsmCallerWriteGenerator.INSTANCE.create(functions(keys), null, -1);
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
        GeneratedCallerWrite<List<String>, String, String> caller =
                AsmCallerWriteGenerator.INSTANCE.create(functions(keys), record("fallback"), Integer.MIN_VALUE);

        List<String> expected = new ArrayList<>();
        for (int key : keys) {
            expected.add("fn" + key + "/c2/c3");
        }
        Assertions.assertEquals(expected, call(caller, script(Integer.MIN_VALUE, keys)));
    }
}
