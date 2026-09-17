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
import org.globsframework.core.model.caller.LoopFromGlobCallerFactory;
import org.globsframework.core.model.caller.FromGlobCallerFactory;
import org.globsframework.core.model.caller.CallerGlobFactory;
import org.globsframework.model.generator.AsmCallerGenerator;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.atomic.AtomicInteger;

/*
What the generated caller is worth against the per-field dispatch a downstream module writes today.

  loop*  : the baseline -- a table of GlobGetAccessor and a table of FieldWrite, both indexed by
           Field.getIndex(), walked in a plain loop. Two call sites for the whole process, each seeing
           every accessor class and every function class : megamorphic, no inlining.
  caller*: the same functions, handed to CallerGlobFactory.create(). One call site per field, each with
           a static final receiver : monomorphic, inlined.
  defaultCaller*: the same functions through LoopFromGlobCallerFactory, i.e. what a type with no generated
           class gets from FromGlobCallerFactory.callerFor. Since the caller's interface is the codec's own,
           that one is a reflective Proxy on top of the loop -- a floor, not a baseline.

Every arm calls exactly the same four FieldWrite classes and produces the same bytes. GlobWrite and
FieldWrite are this benchmark's own interfaces : core fixes the Glob on one side and isSet / isNull / the
value on the other, and what the pass carries -- the SerializedOutput -- is ours.

Run :
  mvn -o test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
  java -cp target/classes:target/test-classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main FromGlobCallerPerf
*/
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class FromGlobCallerPerf {

    /** The caller's interface : the Glob, then what the pass carries. */
    public interface GlobWrite {
        void write(Glob data, SerializedOutput out);
    }

    /** The functions' : the state of the field and its value, then the same pass. */
    public interface FieldWrite {
        void write(boolean isSet, boolean isNull, Object value, SerializedOutput out);
    }

    private static final Class<?>[] ARGS = {SerializedOutput.class};

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
    private FieldWrite[] objectFunctions;
    private FieldWrite[] primitiveFunctions;
    private GlobWrite objectCaller;
    private GlobWrite primitiveCaller;
    private GlobWrite objectDefaultCaller;
    private GlobWrite primitiveDefaultCaller;

    // core's DefaultGlob : nothing generated for the type at all, only the traversal
    private Glob coreGlob;
    private Field[] coreFields;
    private GlobGetAccessor[] coreAccessors;
    private FieldWrite[] coreFunctions;
    private GlobWrite coreCaller;
    private GlobWrite coreDefaultCaller;

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
        objectDefaultCaller = caller(new LoopFromGlobCallerFactory(objectType));
        primitiveDefaultCaller = caller(new LoopFromGlobCallerFactory(primitiveType));

        GlobType coreType = build("core", null);
        coreGlob = fill(coreType);
        coreFields = coreType.getFields();
        coreAccessors = accessorsOf(coreType);
        coreFunctions = functionsOf(coreType);
        coreCaller = caller(AsmCallerGenerator.forDefaultGlob(coreType));
        coreDefaultCaller = caller(new LoopFromGlobCallerFactory(coreType));
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
        if (service != null && !(type.getGlobFactory() instanceof CallerGlobFactory)) {
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

    private FieldWrite[] functionsOf(GlobType type) {
        FieldWrite[] functions = new FieldWrite[type.getFieldCount()];
        for (Field field : type.getFields()) {
            functions[field.getIndex()] = functionFor(field);
        }
        return functions;
    }

    private GlobWrite callerOf(GlobType type) {
        return caller((CallerGlobFactory) type.getGlobFactory());
    }

    private GlobWrite caller(FromGlobCallerFactory factory) {
        return factory.create("perf", functions(), null, GlobWrite.class, FieldWrite.class, ARGS);
    }

    private FromGlobCallerFactory.Functions<FieldWrite> functions() {
        return FromGlobCallerPerf::functionFor;
    }

    private static FieldWrite functionFor(Field field) {
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

    private int loop(Glob glob, Field[] fields, GlobGetAccessor[] accessors, FieldWrite[] functions) {
        output.reset();
        for (int i = 0; i < fields.length; i++) {
            GlobGetAccessor accessor = accessors[i];
            Object value = accessor.getValue(glob);
            functions[i].write(accessor.isSet(glob), value == null, value, output);
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
        objectCaller.write(objectGlob, output);
        return output.position();
    }

    @Benchmark
    public int callerPrimitive() {
        output.reset();
        primitiveCaller.write(primitiveGlob, output);
        return output.position();
    }

    // ---- the fallback shipped for the types that have no generated class ------------------------

    @Benchmark
    public int defaultCallerObject() {
        output.reset();
        objectDefaultCaller.write(objectGlob, output);
        return output.position();
    }

    @Benchmark
    public int defaultCallerPrimitive() {
        output.reset();
        primitiveDefaultCaller.write(primitiveGlob, output);
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
        coreDefaultCaller.write(coreGlob, output);
        return output.position();
    }

    /** the generated caller over a Glob nothing generated : get(int) + isSetAt(int), unrolled */
    @Benchmark
    public int callerCoreGlob() {
        output.reset();
        coreCaller.write(coreGlob, output);
        return output.position();
    }

    // the value is the one argument still an Object -- it is the one whose type changes per field
    static class StringFunction implements FieldWrite {
        public void write(boolean isSet, boolean isNull, Object value, SerializedOutput out) {
            out.writeUtf8String(isNull ? null : (String) value);
        }
    }

    static class IntFunction implements FieldWrite {
        public void write(boolean isSet, boolean isNull, Object value, SerializedOutput out) {
            out.writeInteger(isNull ? 0 : (Integer) value);
        }
    }

    static class DoubleFunction implements FieldWrite {
        public void write(boolean isSet, boolean isNull, Object value, SerializedOutput out) {
            out.writeDouble(isNull ? 0 : (Double) value);
        }
    }

    static class LongFunction implements FieldWrite {
        public void write(boolean isSet, boolean isNull, Object value, SerializedOutput out) {
            out.writeLong(isNull ? 0 : (Long) value);
        }
    }
}
