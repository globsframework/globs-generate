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
import org.globsframework.model.generator.AsmAccessorGenerator;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.atomic.AtomicInteger;

/*
Bytecode accessors (direct GETFIELD / PUTFIELD on the generated Glob) against the doGet / doSet based ones
built by AbstractGeneratedGlobFactory, and against core's DefaultGlob, on the primitive flavour.

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
    private Variant viaDoGet;
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
        generated = build(PRIMITIVE, true);
        viaDoGet = build(PRIMITIVE, false);
        defaultGlob = build(null, true);

        String generatedName = generated.getInt.getClass().getName();
        if (!generatedName.startsWith("org.globsframework.model.generated.")) {
            throw new IllegalStateException("accessors are not generated : " + generatedName);
        }
        if (viaDoGet.getInt.getClass().getName().startsWith("org.globsframework.model.generated.")) {
            throw new IllegalStateException("the fallback variant got generated accessors");
        }
    }

    private Variant build(String service, boolean generateAccessors) {
        if (service == null) {
            System.clearProperty("globs.builder");
        } else {
            System.setProperty("globs.builder", service);
        }
        AsmAccessorGenerator.GENERATE_ACCESSORS = generateAccessors;
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
        AsmAccessorGenerator.GENERATE_ACCESSORS = true;
        GlobFactoryService.Builder.reset();
        return variant;
    }

    @Benchmark
    public int getNativeGenerated() {
        return generated.getInt.getNative(generated.glob);
    }

    @Benchmark
    public int getNativeViaDoGet() {
        return viaDoGet.getInt.getNative(viaDoGet.glob);
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
    public void setNativeViaDoGet() {
        viaDoGet.setInt.setNative(viaDoGet.glob, 42);
    }

    @Benchmark
    public void setNativeDefaultGlob() {
        defaultGlob.setInt.setNative(defaultGlob.glob, 42);
    }

    // isSet / isNull : a mask read at a constant index on the generated accessor, against
    // AbstractGeneratedGetAccessor's glob.isSet(field) and doGet(field) == null (which boxes here).
    @Benchmark
    public boolean isSetGenerated() {
        return generated.getInt.isSet(generated.glob);
    }

    @Benchmark
    public boolean isSetViaDoGet() {
        return viaDoGet.getInt.isSet(viaDoGet.glob);
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
    public boolean isNullViaDoGet() {
        return viaDoGet.getInt.isNull(viaDoGet.glob);
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
    public String getStringViaDoGet() {
        return viaDoGet.getString.get(viaDoGet.glob);
    }

    @Benchmark
    public void setStringGenerated() {
        generated.setString.set(generated.glob, "y");
    }

    @Benchmark
    public void setStringViaDoGet() {
        viaDoGet.setString.set(viaDoGet.glob, "y");
    }
}
