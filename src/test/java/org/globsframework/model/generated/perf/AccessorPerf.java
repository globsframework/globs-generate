package org.globsframework.model.generated.perf;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.GlobFactoryService;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.get.GlobGetIntAccessor;
import org.globsframework.core.model.globaccessor.get.GlobGetStringAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetStringAccessor;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.atomic.AtomicInteger;

/*
Bytecode accessors (direct GETFIELD / PUTFIELD on the generated Glob) against core's DefaultGlob, on the
primitive flavour. The doGet / doSet based arm is gone with the GENERATE_ACCESSORS switch, and so are the
accessors it measured : the factory is built with the generated ones and has no other kind.

Run :
  mvn -o test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
  java -cp target/classes:target/test-classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main AccessorPerf
*/
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class AccessorPerf {
    private static final AtomicInteger UNIQUE = new AtomicInteger();
    private static final String PRIMITIVE = "org.globsframework.model.generator.primitive.GeneratorGlobFactoryService";

    private Variant generated;
    private Variant wide;
    private Variant defaultGlob;

    static class Variant {
        MutableGlob glob;
        GlobGetIntAccessor getInt;
        GlobSetIntAccessor setInt;
        GlobGetStringAccessor getString;
        GlobSetStringAccessor setString;
    }

    @Setup
    public void setUp() {
        generated = build(PRIMITIVE);
        wide = buildWide();
        defaultGlob = build(null);

        String generatedName = generated.getInt.getClass().getName();
        if (!generatedName.startsWith("org.globsframework.model.generated.")) {
            throw new IllegalStateException("accessors are not generated : " + generatedName);
        }
    }

    // same accessor on field 0, but the type has 40 fields so the Glob uses the long-mask base
    private Variant buildWide() {
        System.setProperty("globs.builder", PRIMITIVE);
        GlobFactoryService.Builder.reset();
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("Wide_" + UNIQUE.incrementAndGet());
        IntegerField intField = builder.declareIntegerField("count");
        for (int i = 1; i < 40; i++) {
            builder.declareIntegerField("f" + i);
        }
        GlobType type = builder.build();
        Variant variant = new Variant();
        variant.glob = type.instantiate().set(intField, 1_000_000);
        variant.getInt = type.getGlobFactory().getGetAccessor(intField);
        System.clearProperty("globs.builder");
        GlobFactoryService.Builder.reset();
        return variant;
    }

    private Variant build(String service) {
        if (service == null) {
            System.clearProperty("globs.builder");
        } else {
            System.setProperty("globs.builder", service);
        }
        GlobFactoryService.Builder.reset();

        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("Acc_" + UNIQUE.incrementAndGet());
        IntegerField intField = builder.declareIntegerField("count");
        StringField stringField = builder.declareStringField("label");
        GlobType type = builder.build();

        Variant variant = new Variant();
        // outside the Integer cache on purpose : the doGet-based isNull boxes, and a cached 1 would hide it
        variant.glob = type.instantiate().set(intField, 1_000_000).set(stringField, "x");
        variant.getInt = type.getGlobFactory().getGetAccessor(intField);
        variant.setInt = type.getGlobFactory().getSetAccessor(intField);
        variant.getString = (GlobGetStringAccessor) type.getGlobFactory().getGetValueAccessor(stringField);
        variant.setString = (GlobSetStringAccessor) type.getGlobFactory().getSetValueAccessor(stringField);

        System.clearProperty("globs.builder");
        GlobFactoryService.Builder.reset();
        return variant;
    }

    @Benchmark
    public int getNativeGenerated() {
        return generated.getInt.getNative(generated.glob);
    }

    @Benchmark
    public int getNativeDefaultGlob() {
        return defaultGlob.getInt.getNative(defaultGlob.glob);
    }

    @Benchmark
    public void setNativeGenerated() {
        generated.setInt.setNative(generated.glob, 42);
    }

    @Benchmark
    public void setNativeDefaultGlob() {
        defaultGlob.setInt.setNative(defaultGlob.glob, 42);
    }

    // isSet / isNull : a mask read at a constant index on the generated accessor. The 64 pair is the same
    // accessor on the same field of a 40-field type, i.e. the long mask, to check the two widths tie.
    @Benchmark
    public boolean isSetGenerated() {
        return generated.getInt.isSet(generated.glob);
    }

    @Benchmark
    public boolean isSetGenerated64() {
        return wide.getInt.isSet(wide.glob);
    }

    @Benchmark
    public boolean isNullGenerated64() {
        return wide.getInt.isNull(wide.glob);
    }

    @Benchmark
    public boolean isSetDefaultGlob() {
        return defaultGlob.getInt.isSet(defaultGlob.glob);
    }

    @Benchmark
    public boolean isNullGenerated() {
        return generated.getInt.isNull(generated.glob);
    }

    @Benchmark
    public boolean isNullDefaultGlob() {
        return defaultGlob.getInt.isNull(defaultGlob.glob);
    }

    @Benchmark
    public String getStringGenerated() {
        return generated.getString.get(generated.glob);
    }

    @Benchmark
    public String getStringDefaultGlob() {
        return defaultGlob.getString.get(defaultGlob.glob);
    }

    @Benchmark
    public void setStringGenerated() {
        generated.setString.set(generated.glob, "y");
    }

    @Benchmark
    public void setStringDefaultGlob() {
        defaultGlob.setString.set(defaultGlob.glob, "y");
    }

}
