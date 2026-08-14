package org.globsframework.model.generated.perf;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.DoubleField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.generate.write.CallAtWrite;
import org.globsframework.core.model.generate.write.DefaultFunctionCallerWrite;
import org.globsframework.core.model.generate.write.GeneratedCallerWrite;
import org.globsframework.core.model.generate.write.GeneratedCallerWriteAll;
import org.globsframework.core.model.generate.write.MutableFunctionWrite;
import org.globsframework.core.utils.serialization.ByteBufferSerializationInput;
import org.globsframework.core.utils.serialization.ByteBufferSerializationOutput;
import org.globsframework.core.utils.serialization.SerializedInput;
import org.globsframework.model.generator.AsmCallerWriteGenerator;
import org.openjdk.jmh.annotations.*;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/*
The write side of CallerPerf : what the generated switch is worth against the loop a parser writes today.
One pass = one record, i.e. fieldCount calls, each reading its value from a SerializedInput and setting it on
a MutableGlob -- exactly what a parser does, so the dispatch is measured with real work around it.

  loopArray / loopHash : the baseline a parser writes by hand. One call site for the whole loop, seeing the
           four function classes : megamorphic, no inlining. Array indexing is the best case (keys are the
           field indices); a HashMap is the general one (keys are ids or hashes) and pays a boxed lookup.
  defaultCaller*: the same functions in core's DefaultFunctionCallerWrite -- what a JVM without
           -Dglobs.callerWrite gets. Same single call site, keys binary-searched.
  generatedCaller*: the same functions through AsmCallerWriteGenerator. One call site per key, each with a
           static final receiver : monomorphic, inlined. Dense keys give a tableswitch, sparse a lookupswitch.
  *All   : the other shape, with no CallAt to follow -- the array loop against the unrolled one.

Every arm calls exactly the same four MutableFunctionWrite classes, over the same payload, and leaves the
same Glob behind. The Glob is core's DefaultGlob : nothing here reads its layout, so -Dglobs.builder is
orthogonal to what is measured (it only changes the cost of the set, identically on every arm).

Run :
  mvn -o test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
  java -cp target/classes:target/test-classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main CallerWritePerf
*/
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class CallerWritePerf {
    private static final AtomicInteger UNIQUE = new AtomicInteger();
    private static final int END = -1;
    /** sparse keys : far enough apart that the switch cannot be a table, and out of the Integer cache */
    private static final int SPREAD = 977;

    // 40 is where the emitted switch stops being small
    @Param({"4", "20", "40"})
    public int fieldCount;

    private ByteBufferSerializationInput input;
    private int payloadLength;
    private MutableGlob glob;

    // keys are the field indices : the parser can index an array with them
    private Script denseScript;
    private MutableFunctionWrite<SerializedInput, Void, Void>[] byIndex;
    private GeneratedCallerWrite<SerializedInput, Void, Void> denseDefaultCaller;
    private GeneratedCallerWrite<SerializedInput, Void, Void> denseGeneratedCaller;

    // keys are ids of the format : a map on one side, a lookupswitch on the other
    private Script sparseScript;
    private Map<Integer, MutableFunctionWrite<SerializedInput, Void, Void>> byKey;
    private GeneratedCallerWrite<SerializedInput, Void, Void> sparseDefaultCaller;
    private GeneratedCallerWrite<SerializedInput, Void, Void> sparseGeneratedCaller;

    // no CallAt : every function once, in order
    private MutableFunctionWrite<SerializedInput, Void, Void>[] allFunctions;
    private GeneratedCallerWriteAll<SerializedInput, Void, Void> defaultCallerAll;
    private GeneratedCallerWriteAll<SerializedInput, Void, Void> generatedCallerAll;

    @SuppressWarnings("unchecked")
    @Setup
    public void setUp() {
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("CallerWrite_" + UNIQUE.incrementAndGet());
        for (int i = 0; i < fieldCount; i++) {
            switch (i % 4) {
                case 0 -> builder.declareStringField("s" + i);
                case 1 -> builder.declareIntegerField("i" + i);
                case 2 -> builder.declareDoubleField("d" + i);
                default -> builder.declareLongField("l" + i);
            }
        }
        GlobType type = builder.build();
        glob = type.instantiate();

        byte[] payload = new byte[1024 * 1024];
        ByteBufferSerializationOutput output = new ByteBufferSerializationOutput(payload);
        for (Field field : type.getFields()) {
            if (field instanceof StringField) {
                output.writeUtf8String("value" + field.getIndex());
            } else if (field instanceof IntegerField) {
                output.write(field.getIndex());
            } else if (field instanceof DoubleField) {
                output.write(field.getIndex() * 1.5);
            } else {
                output.write((long) field.getIndex());
            }
        }
        payloadLength = output.position();
        input = new ByteBufferSerializationInput(payload, payloadLength);

        allFunctions = new MutableFunctionWrite[fieldCount];
        byIndex = new MutableFunctionWrite[fieldCount];
        byKey = new HashMap<>();
        SortedMap<Integer, MutableFunctionWrite<SerializedInput, Void, Void>> dense = new TreeMap<>();
        SortedMap<Integer, MutableFunctionWrite<SerializedInput, Void, Void>> sparse = new TreeMap<>();
        int[] denseKeys = new int[fieldCount];
        int[] sparseKeys = new int[fieldCount];
        for (Field field : type.getFields()) {
            int index = field.getIndex();
            MutableFunctionWrite<SerializedInput, Void, Void> function = functionFor(field);
            allFunctions[index] = function;
            byIndex[index] = function;
            denseKeys[index] = index;
            sparseKeys[index] = index * SPREAD;
            dense.put(index, function);
            sparse.put(index * SPREAD, function);
            byKey.put(index * SPREAD, function);
        }
        // the payload is written in field order, so both scripts walk the keys in that order
        denseScript = new Script(denseKeys);
        sparseScript = new Script(sparseKeys);

        denseDefaultCaller = DefaultFunctionCallerWrite.INSTANCE.create(dense, null, END);
        denseGeneratedCaller = AsmCallerWriteGenerator.INSTANCE.create(dense, null, END);
        sparseDefaultCaller = DefaultFunctionCallerWrite.INSTANCE.create(sparse, null, END);
        sparseGeneratedCaller = AsmCallerWriteGenerator.INSTANCE.create(sparse, null, END);
        defaultCallerAll = DefaultFunctionCallerWrite.INSTANCE.create(allFunctions);
        generatedCallerAll = AsmCallerWriteGenerator.INSTANCE.create(allFunctions);
    }

    private static MutableFunctionWrite<SerializedInput, Void, Void> functionFor(Field field) {
        if (field instanceof StringField f) {
            return new StringFunction(f);
        } else if (field instanceof IntegerField f) {
            return new IntFunction(f);
        } else if (field instanceof DoubleField f) {
            return new DoubleFunction(f);
        } else {
            return new LongFunction((LongField) field);
        }
    }

    /** What the CallAt of a parser is : the next key of the record, then the end of it. */
    private static final class Script implements CallAtWrite {
        private final int[] keys;
        private int at;

        Script(int[] keys) {
            this.keys = keys;
        }

        void reset() {
            at = 0;
        }

        public int getNextToCall() {
            return at < keys.length ? keys[at++] : END;
        }
    }

    private int start(Script script) {
        input.reset(0, payloadLength);
        script.reset();
        return 0;
    }

    // ---- the baseline : one call site for the whole loop, megamorphic --------------------------

    @Benchmark
    public int loopArray() {
        start(denseScript);
        int next;
        while ((next = denseScript.getNextToCall()) != END) {
            byIndex[next].call(glob, input, null, null);
        }
        return input.position();
    }

    @Benchmark
    public int loopHash() {
        start(sparseScript);
        int next;
        while ((next = sparseScript.getNextToCall()) != END) {
            byKey.get(next).call(glob, input, null, null);
        }
        return input.position();
    }

    // ---- what a JVM without -Dglobs.callerWrite gets --------------------------------------------

    @Benchmark
    public int defaultCallerDense() {
        start(denseScript);
        denseDefaultCaller.call(denseScript, glob, input, null, null);
        return input.position();
    }

    @Benchmark
    public int defaultCallerSparse() {
        start(sparseScript);
        sparseDefaultCaller.call(sparseScript, glob, input, null, null);
        return input.position();
    }

    // ---- the same functions, through the generated switch ---------------------------------------

    /** dense keys : a tableswitch */
    @Benchmark
    public int generatedCallerDense() {
        start(denseScript);
        denseGeneratedCaller.call(denseScript, glob, input, null, null);
        return input.position();
    }

    /** sparse keys : a lookupswitch */
    @Benchmark
    public int generatedCallerSparse() {
        start(sparseScript);
        sparseGeneratedCaller.call(sparseScript, glob, input, null, null);
        return input.position();
    }

    // ---- the other shape : no CallAt, every function once ---------------------------------------

    @Benchmark
    public int loopAll() {
        input.reset(0, payloadLength);
        for (MutableFunctionWrite<SerializedInput, Void, Void> function : allFunctions) {
            function.call(glob, input, null, null);
        }
        return input.position();
    }

    @Benchmark
    public int defaultCallerAll() {
        input.reset(0, payloadLength);
        defaultCallerAll.call(glob, input, null, null);
        return input.position();
    }

    @Benchmark
    public int generatedCallerAll() {
        input.reset(0, payloadLength);
        generatedCallerAll.call(glob, input, null, null);
        return input.position();
    }

    record StringFunction(StringField field) implements MutableFunctionWrite<SerializedInput, Void, Void> {
        public void call(MutableGlob glob, SerializedInput in, Void ignored, Void alsoIgnored) {
            glob.set(field, in.readUtf8String());
        }
    }

    record IntFunction(IntegerField field) implements MutableFunctionWrite<SerializedInput, Void, Void> {
        public void call(MutableGlob glob, SerializedInput in, Void ignored, Void alsoIgnored) {
            glob.set(field, in.readNotNullInt());
        }
    }

    record DoubleFunction(DoubleField field) implements MutableFunctionWrite<SerializedInput, Void, Void> {
        public void call(MutableGlob glob, SerializedInput in, Void ignored, Void alsoIgnored) {
            glob.set(field, in.readNotNullDouble());
        }
    }

    record LongFunction(LongField field) implements MutableFunctionWrite<SerializedInput, Void, Void> {
        public void call(MutableGlob glob, SerializedInput in, Void ignored, Void alsoIgnored) {
            glob.set(field, in.readNotNullLong());
        }
    }
}
