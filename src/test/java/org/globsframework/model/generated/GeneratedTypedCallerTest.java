package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.LoopToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.model.generator.AsmCallerWriteGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The typed shape : the emitted class implements the caller's own interface and calls the caller's own
 * function interface, so the arguments keep their types — the point being that a {@code long} stays a
 * {@code long} and that there is no adapter object between the caller and the function.
 * <p>
 * The two interfaces here name their methods differently on purpose : they are written independently, and
 * what ties them together is the parameter list.
 */
public class GeneratedTypedCallerTest {

    public interface RecordReader {
        void read(MutableGlob data, long offset, double scale, List<String> trace);
    }

    public interface FieldRead {
        void readAtOffset(MutableGlob data, long offset, double scale, List<String> trace);
    }

    /** No {@code long} anywhere : the shape has to work for the ordinary case too. */
    public interface Simple {
        void call(List<String> trace);
    }

    public interface SimpleFunction {
        void run(List<String> trace);
    }

    private final GlobType type;
    private final IntegerField count;

    public GeneratedTypedCallerTest() {
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("TypedTarget");
        count = builder.declareIntegerField("count");
        type = builder.build();
    }

    private FieldRead record(String label) {
        return (data, offset, scale, trace) -> trace.add(label + "@" + offset + "x" + scale);
    }

    private RecordReader reader(ToGlobCallerFactory factory, FieldRead... functions) {
        return factory.create("test.typed", functions, RecordReader.class, FieldRead.class,
                MutableGlob.class, long.class, double.class, List.class);
    }

    /** The generated caller and the fallback have to be indistinguishable from the outside. */
    @Test
    public void everyFunctionIsCalledOnceInOrderWithItsArgumentsUnchanged() {
        for (ToGlobCallerFactory factory :
                new ToGlobCallerFactory[]{AsmCallerWriteGenerator.INSTANCE, LoopToGlobCallerFactory.INSTANCE}) {
            List<String> trace = new ArrayList<>();
            reader(factory, record("a"), record("b"), record("c"))
                    .read(type.instantiate(), 42L, 0.5d, trace);

            Assertions.assertEquals(List.of("a@42x0.5", "b@42x0.5", "c@42x0.5"), trace,
                    factory.getClass().getName());
        }
    }

    /** A long takes two slots and a double two more : the wrong slot arithmetic shows up right here. */
    @Test
    public void wideArgumentsLandInTheRightSlots() {
        List<String> trace = new ArrayList<>();
        reader(AsmCallerWriteGenerator.INSTANCE,
                (data, offset, scale, t) -> t.add(offset + "/" + scale),
                (data, offset, scale, t) -> t.add((offset + 1) + "/" + (scale * 2)))
                .read(null, Long.MAX_VALUE, 1.25d, trace);

        Assertions.assertEquals(List.of(Long.MAX_VALUE + "/1.25", Long.MIN_VALUE + "/2.5"), trace);
    }

    @Test
    public void theFunctionsReallyWriteIntoTheGlobTheyAreHanded() {
        MutableGlob glob = type.instantiate();
        reader(AsmCallerWriteGenerator.INSTANCE,
                (data, offset, scale, trace) -> data.set(count, (int) offset))
                .read(glob, 7L, 1d, new ArrayList<>());

        Assertions.assertEquals(7, glob.get(count).intValue());
    }

    @Test
    public void nothingToCallIsAnEmptyCall() {
        List<String> trace = new ArrayList<>();
        reader(AsmCallerWriteGenerator.INSTANCE).read(null, 0L, 0d, trace);
        Assertions.assertTrue(trace.isEmpty());
    }

    @Test
    public void aShapeWithoutPrimitivesWorksToo() {
        List<String> trace = new ArrayList<>();
        Simple caller = AsmCallerWriteGenerator.INSTANCE.create("test.simple",
                new SimpleFunction[]{t -> t.add("one"), t -> t.add("two")},
                Simple.class, SimpleFunction.class, List.class);
        caller.call(trace);

        Assertions.assertEquals(List.of("one", "two"), trace);
    }

    /** Same design as the other callers : one class per create, functions in static finals the JIT folds. */
    @Test
    public void aClassPerCreateHoldingItsFunctionsInStaticFinals() throws Exception {
        Class<?> first = reader(AsmCallerWriteGenerator.INSTANCE, record("a")).getClass();
        Class<?> second = reader(AsmCallerWriteGenerator.INSTANCE, record("a")).getClass();

        Assertions.assertNotSame(first, second);
        int modifiers = first.getDeclaredField("fn_0").getModifiers();
        Assertions.assertTrue(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
        Assertions.assertEquals(FieldRead.class, first.getDeclaredField("fn_0").getType());
        // the emitted method is the interface's own, with the interface's own descriptor
        Method emitted = first.getDeclaredMethod("read", MutableGlob.class, long.class, double.class,
                List.class);
        Assertions.assertEquals(void.class, emitted.getReturnType());
    }

    @Test
    public void theRefusalsAreTheSameAsTheFallbackOne() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> reader(AsmCallerWriteGenerator.INSTANCE, record("a"), null));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create("test.typed", new FieldRead[0],
                        String.class, FieldRead.class, MutableGlob.class, long.class, double.class,
                        List.class));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create("", new FieldRead[0], RecordReader.class,
                        FieldRead.class, MutableGlob.class, long.class, double.class, List.class));
        // a shape the two types do not share
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AsmCallerWriteGenerator.INSTANCE.create("test.typed", new FieldRead[0],
                        RecordReader.class, FieldRead.class, MutableGlob.class, Long.class, double.class,
                        List.class));
    }
}
