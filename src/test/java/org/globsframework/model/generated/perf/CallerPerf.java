package org.globsframework.model.generated.perf;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.GlobFactoryService;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.core.utils.serialization.ByteBufferSerializationOutput;
import org.globsframework.core.utils.serialization.SerializedOutput;
import org.globsframework.core.model.generate.read.DefaultFunctionCaller;
import org.globsframework.core.model.generate.read.FieldValueFunction;
import org.globsframework.core.model.generate.read.GenerateCaller;
import org.globsframework.core.model.generate.read.GeneratedFunctionCaller;
import org.globsframework.core.model.generate.read.GlobGenerateFactory;
import org.globsframework.model.generator.AsmCallerGenerator;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.atomic.AtomicInteger;

/*
What the generated caller is worth against the per-field dispatch a downstream module writes today.

  loop*  : the baseline -- a table of GlobGetAccessor and a table of FieldValueFunction, both indexed by
           Field.getIndex(), walked in a plain loop. Two call sites for the whole process, each seeing
           every accessor class and every function class : megamorphic, no inlining.
  caller*: the same functions, handed to GlobGenerateFactory.create(). One call site per field, each with
           a static final receiver : monomorphic, inlined.
  defaultCaller*: the same functions in a DefaultFunctionCaller, i.e. what a type with no generated class
           gets from GenerateCaller.callerFor. Same shape as the baseline, through Glob.getValue.

Both arms call exactly the same four FieldValueFunction classes and produce the same bytes.

Run :
  mvn -o test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
  java -cp target/classes:target/test-classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main CallerPerf
*/
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class CallerPerf {
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    // 40 crosses into the long isSet mask
    @Param({"4", "20", "40"})
    public int fieldCount;

    private ByteBufferSerializationOutput output;

    private Glob objectGlob;
    private Glob primitiveGlob;
    private Field[] objectFields;
    private Field[] primitiveFields;
    private GlobGetAccessor[] objectAccessors;
    private GlobGetAccessor[] primitiveAccessors;
    private FieldValueFunction[] objectFunctions;
    private FieldValueFunction[] primitiveFunctions;
    private GeneratedFunctionCaller<SerializedOutput, Void> objectCaller;
    private GeneratedFunctionCaller<SerializedOutput, Void> primitiveCaller;
    private GeneratedFunctionCaller<SerializedOutput, Void> objectDefaultCaller;
    private GeneratedFunctionCaller<SerializedOutput, Void> primitiveDefaultCaller;

    // core's DefaultGlob : nothing generated for the type at all, only the traversal
    private Glob coreGlob;
    private Field[] coreFields;
    private GlobGetAccessor[] coreAccessors;
    private FieldValueFunction[] coreFunctions;
    private GeneratedFunctionCaller<SerializedOutput, Void> coreCaller;
    private GeneratedFunctionCaller<SerializedOutput, Void> coreDefaultCaller;

    @Setup
    public void setUp() {
        output = new ByteBufferSerializationOutput(new byte[1024 * 1024]);

        GlobType objectType = build("object", "org.globsframework.model.generator.object.GeneratorGlobFactoryService");
        GlobType primitiveType = build("primitive", "org.globsframework.model.generator.primitive.GeneratorGlobFactoryService");

        objectGlob = fill(objectType);
        primitiveGlob = fill(primitiveType);
        objectFields = objectType.getFields();
        primitiveFields = primitiveType.getFields();
        objectAccessors = accessorsOf(objectType);
        primitiveAccessors = accessorsOf(primitiveType);
        objectFunctions = functionsOf(objectType);
        primitiveFunctions = functionsOf(primitiveType);
        objectCaller = callerOf(objectType);
        primitiveCaller = callerOf(primitiveType);
        objectDefaultCaller = new DefaultFunctionCaller<>(objectType, functions());
        primitiveDefaultCaller = new DefaultFunctionCaller<>(primitiveType, functions());

        GlobType coreType = build("core", null);
        coreGlob = fill(coreType);
        coreFields = coreType.getFields();
        coreAccessors = accessorsOf(coreType);
        coreFunctions = functionsOf(coreType);
        coreCaller = AsmCallerGenerator.forDefaultGlob(coreType).create(functions());
        coreDefaultCaller = new DefaultFunctionCaller<>(coreType, functions());
    }

    /** service == null : core's DefaultGlob, no generation of any kind for the type. */
    private GlobType build(String tag, String service) {
        if (service == null) {
            System.clearProperty("globs.builder");
        } else {
            System.setProperty("globs.builder", service);
        }
        GlobFactoryService.Builder.reset();
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("Caller_" + tag + "_" + UNIQUE.incrementAndGet());
        for (int i = 0; i < fieldCount; i++) {
            switch (i % 4) {
                case 0 -> builder.declareStringField("s" + i);
                case 1 -> builder.declareIntegerField("i" + i);
                case 2 -> builder.declareDoubleField("d" + i);
                default -> builder.declareLongField("l" + i);
            }
        }
        GlobType type = builder.build();
        if (service != null && !(type.getGlobFactory() instanceof GlobGenerateFactory)) {
            throw new IllegalStateException("generation is inert for " + tag + " : " + type.getGlobFactory().getClass());
        }
        System.clearProperty("globs.builder");
        GlobFactoryService.Builder.reset();
        return type;
    }

    private MutableGlob fill(GlobType type) {
        MutableGlob glob = type.instantiate();
        for (Field field : type.getFields()) {
            if (field instanceof StringField f) {
                glob.set(f, "value" + field.getIndex());
            } else if (field instanceof IntegerField f) {
                glob.set(f, field.getIndex());
            } else if (field instanceof DoubleField f) {
                glob.set(f, field.getIndex() * 1.5);
            } else if (field instanceof LongField f) {
                glob.set(f, (long) field.getIndex());
            }
        }
        return glob;
    }

    private GlobGetAccessor[] accessorsOf(GlobType type) {
        GlobGetAccessor[] accessors = new GlobGetAccessor[type.getFieldCount()];
        for (Field field : type.getFields()) {
            accessors[field.getIndex()] = type.getGlobFactory().getGetValueAccessor(field);
        }
        return accessors;
    }

    private FieldValueFunction[] functionsOf(GlobType type) {
        FieldValueFunction[] functions = new FieldValueFunction[type.getFieldCount()];
        for (Field field : type.getFields()) {
            functions[field.getIndex()] = functionFor(field);
        }
        return functions;
    }

    private GeneratedFunctionCaller<SerializedOutput, Void> callerOf(GlobType type) {
        return ((GlobGenerateFactory) type.getGlobFactory()).create(functions());
    }

    private GenerateCaller.GetFieldValueFunction<SerializedOutput, Void> functions() {
        return new GenerateCaller.GetFieldValueFunction<>() {
            @SuppressWarnings("unchecked")
            public <T> FieldValueFunction<T, SerializedOutput, Void> create(Field field) {
                return (FieldValueFunction<T, SerializedOutput, Void>) functionFor(field);
            }
        };
    }

    @SuppressWarnings("rawtypes")
    private static FieldValueFunction functionFor(Field field) {
        if (field instanceof StringField) {
            return new StringFunction();
        } else if (field instanceof IntegerField) {
            return new IntFunction();
        } else if (field instanceof DoubleField) {
            return new DoubleFunction();
        } else {
            return new LongFunction();
        }
    }

    // ---- the baseline : one accessor call site and one function call site, both megamorphic ----

    @SuppressWarnings("unchecked")
    private int loop(Glob glob, Field[] fields, GlobGetAccessor[] accessors, FieldValueFunction[] functions) {
        output.reset();
        for (int i = 0; i < fields.length; i++) {
            GlobGetAccessor accessor = accessors[i];
            Object value = accessor.getValue(glob);
            functions[i].call(accessor.isSet(glob), value == null, value, output, null);
        }
        return output.position();
    }

    @Benchmark
    public int loopObject() {
        return loop(objectGlob, objectFields, objectAccessors, objectFunctions);
    }

    @Benchmark
    public int loopPrimitive() {
        return loop(primitiveGlob, primitiveFields, primitiveAccessors, primitiveFunctions);
    }

    // ---- the same functions, through the generated caller --------------------------------------

    @Benchmark
    public int callerObject() {
        output.reset();
        objectCaller.call(objectGlob, output, null);
        return output.position();
    }

    @Benchmark
    public int callerPrimitive() {
        output.reset();
        primitiveCaller.call(primitiveGlob, output, null);
        return output.position();
    }

    // ---- the fallback shipped for the types that have no generated class ------------------------

    @Benchmark
    public int defaultCallerObject() {
        output.reset();
        objectDefaultCaller.call(objectGlob, output, null);
        return output.position();
    }

    @Benchmark
    public int defaultCallerPrimitive() {
        output.reset();
        primitiveDefaultCaller.call(primitiveGlob, output, null);
        return output.position();
    }

    // ---- core's DefaultGlob : the three ways to walk it ----------------------------------------

    @Benchmark
    public int loopCoreGlob() {
        return loop(coreGlob, coreFields, coreAccessors, coreFunctions);
    }

    @Benchmark
    public int defaultCallerCoreGlob() {
        output.reset();
        coreDefaultCaller.call(coreGlob, output, null);
        return output.position();
    }

    /** the generated caller over a Glob nothing generated : get(int) + isSetAt(int), unrolled */
    @Benchmark
    public int callerCoreGlob() {
        output.reset();
        coreCaller.call(coreGlob, output, null);
        return output.position();
    }

    static class StringFunction implements FieldValueFunction<String, SerializedOutput, Void> {
        public void call(boolean isSet, boolean isNull, String value, SerializedOutput out, Void ignored) {
            out.writeUtf8String(isNull ? null : value);
        }
    }

    static class IntFunction implements FieldValueFunction<Integer, SerializedOutput, Void> {
        public void call(boolean isSet, boolean isNull, Integer value, SerializedOutput out, Void ignored) {
            out.writeInteger(isNull ? 0 : value);
        }
    }

    static class DoubleFunction implements FieldValueFunction<Double, SerializedOutput, Void> {
        public void call(boolean isSet, boolean isNull, Double value, SerializedOutput out, Void ignored) {
            out.writeDouble(isNull ? 0 : value);
        }
    }

    static class LongFunction implements FieldValueFunction<Long, SerializedOutput, Void> {
        public void call(boolean isSet, boolean isNull, Long value, SerializedOutput out, Void ignored) {
            out.writeLong(isNull ? 0 : value);
        }
    }
}
