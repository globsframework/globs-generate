package org.globsframework.model.generated.perf;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.DoubleField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.FieldValueVisitor;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.GlobFactoryService;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.serialization.ByteBufferSerializationOutput;
import org.globsframework.core.utils.serialization.GlobSerializer;
import org.globsframework.core.utils.serialization.SerializedOutput;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.atomic.AtomicInteger;

/*
What the unrolled field traversal inside the generated Glob is worth. Both flavours emit it now, so the
comparison is object vs primitive vs core's DefaultGlob -- the looped arm is gone with the
UNROLL_VISITORS switch. The looped versions it used to measure still live in AbstractGlob, as the
fallback for anything that does not generate the method.

  serializeLoop*  : GlobSerializer.writeKnowGlob -- core's serializer, which drives the loop itself
                    over type.getFields() and never calls glob.accept.
  serializeAccept*: the same output produced through glob.accept, i.e. what serialization would cost
                    if it were routed through the unrolled visitor.

Run :
  mvn -o test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
  java -cp target/classes:target/test-classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main VisitorUnrollPerf
*/
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class VisitorUnrollPerf {
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    // 40 crosses into AbstractGeneratedGlob64, i.e. the long isSet mask
    @Param({"4", "20", "40"})
    public int fieldCount;

    private Glob object;
    private Glob primitive;
    private Glob defaultGlob;

    private ByteBufferSerializationOutput output;
    private GlobSerializer serializer;
    private CountingVisitor counting;
    private SerializingVisitor serializing;

    @Setup
    public void setUp() {
        object = build("object", "org.globsframework.model.generator.object.GeneratorGlobFactoryService");
        primitive = build("primitive", "org.globsframework.model.generator.primitive.GeneratorGlobFactoryService");
        defaultGlob = build("default", null);

        if (object.getClass() == defaultGlob.getClass() || primitive.getClass() == defaultGlob.getClass()) {
            throw new IllegalStateException("generation is inert : object=" + object.getClass()
                    + " primitive=" + primitive.getClass() + " default=" + defaultGlob.getClass());
        }

        output = new ByteBufferSerializationOutput(new byte[1024 * 1024]);
        serializer = new GlobSerializer(output);
        counting = new CountingVisitor();
        serializing = new SerializingVisitor();
    }

    /** Builds a type while the given factory service is active, and forces the bytecode to be generated
     *  now (the glob class is only emitted on the first create). */
    private Glob build(String tag, String service) {
        if (service == null) {
            System.clearProperty("globs.builder");
        } else {
            System.setProperty("globs.builder", service);
        }
        GlobFactoryService.Builder.reset();

        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("Bench_" + tag + "_" + UNIQUE.incrementAndGet());
        for (int i = 0; i < fieldCount; i++) {
            switch (i % 3) {
                case 0 -> builder.declareStringField("s" + i);
                case 1 -> builder.declareIntegerField("i" + i);
                default -> builder.declareDoubleField("d" + i);
            }
        }
        GlobType type = builder.build();
        MutableGlob glob = type.instantiate();
        for (Field field : type.getFields()) {
            if (field instanceof StringField f) {
                glob.set(f, "value" + field.getIndex());
            } else if (field instanceof IntegerField f) {
                glob.set(f, field.getIndex());
            } else if (field instanceof DoubleField f) {
                glob.set(f, field.getIndex() * 1.5);
            }
        }

        System.clearProperty("globs.builder");
        GlobFactoryService.Builder.reset();
        return glob;
    }

    // ---- glob.accept, counting only : the traversal on its own ---------------------------------
//
//    @Benchmark
//    public int acceptObject() {
//        counting.count = 0;
//        object.safeAccept(counting);
//        return counting.count;
//    }
//
//    @Benchmark
//    public int acceptPrimitive() {
//        counting.count = 0;
//        primitive.safeAccept(counting);
//        return counting.count;
//    }
//
//    @Benchmark
//    public int acceptDefaultGlob() {
//        counting.count = 0;
//        defaultGlob.safeAccept(counting);
//        return counting.count;
//    }

    // ---- serialization as it is written today : drives the loop itself, ignores glob.accept ----
//
//    @Benchmark
//    public int serializeLoopObject() {
//        output.reset();
//        serializer.writeKnowGlob(object);
//        return output.position();
//    }
//
//    @Benchmark
//    public int serializeLoopPrimitive() {
//        output.reset();
//        serializer.writeKnowGlob(primitive);
//        return output.position();
//    }
//
    @Benchmark
    public int serializeLoopDefaultGlob() {
        output.reset();
        serializer.writeKnowGlob(defaultGlob);
        return output.position();
    }

    // ---- the same bytes, produced through glob.accept ------------------------------------------

    @Benchmark
    public int serializeAcceptObject() {
        output.reset();
        serializing.output = output;
        object.safeAccept(serializing);
        return output.position();
    }

    @Benchmark
    public int serializeAcceptPrimitive() {
        output.reset();
        serializing.output = output;
        primitive.safeAccept(serializing);
        return output.position();
    }

    static class CountingVisitor extends FieldValueVisitor.AbstractFieldValueVisitor {
        int count;

        public void notManaged(Field field, Object value) {
            count++;
            Blackhole.consumeCPU(0);
        }
    }

    static class SerializingVisitor extends FieldValueVisitor.AbstractFieldValueVisitor {
        SerializedOutput output;

        public void visitString(StringField field, String value) {
            output.writeUtf8String(value);
        }

        public void visitInteger(IntegerField field, Integer value) {
            output.writeInteger(value);
        }

        public void visitDouble(DoubleField field, Double value) {
            output.writeDouble(value);
        }

        public void notManaged(Field field, Object value) {
            throw new IllegalStateException("unexpected field " + field);
        }
    }
}
