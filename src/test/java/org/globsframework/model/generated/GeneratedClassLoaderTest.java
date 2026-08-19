package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.GlobFactory;
import org.globsframework.core.model.GlobFactoryService;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.FromGlobFunction;
import org.globsframework.core.model.caller.FromGlobCallerFactory;
import org.globsframework.core.model.caller.CallerGlobFactory;
import org.globsframework.core.model.caller.ToGlobFunction;
import org.globsframework.model.generator.AsmCallerWriteGenerator;
import org.globsframework.model.generator.GeneratedClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The loader half of what an AOT cache needs : a class of a user-defined loader is matched on its loader as
 * well as on its own name and bytes, and an anonymous instance conjured per generation — what this replaced —
 * has no identity to match with.
 */
public class GeneratedClassLoaderTest {

    @AfterEach
    public void tearDown() {
        System.clearProperty("globs.builder");
        GlobFactoryService.Builder.reset();
    }

    /** One named loader, and everything this module emits in it : no anonymous class, no child chain. */
    @Test
    public void everythingGeneratedLandsInOneNamedLoader() {
        GlobType type = generated("LoaderOne", 5);
        GlobFactory factory = type.getGlobFactory();
        Field field = type.getFields()[2];

        ClassLoader loader = type.instantiate().getClass().getClassLoader();
        Assertions.assertSame(GeneratedClassLoader.get(), loader);
        Assertions.assertEquals("globs-generated", loader.getName());
        Assertions.assertEquals(GeneratedClassLoader.class, loader.getClass(),
                "an anonymous loader is what this replaced : " + loader.getClass().getName());

        for (Object generated : List.of(
                factory,
                factory.getGetValueAccessor(field),
                factory.getSetValueAccessor(field),
                ((CallerGlobFactory) factory).create("loader.read", recorder()),
                AsmCallerWriteGenerator.INSTANCE.create("loader.write", writeFunctions(), null, -1))) {
            Assertions.assertSame(loader, generated.getClass().getClassLoader(),
                    generated.getClass().getName());
        }
    }

    /**
     * The caller used to need a child of the Glob's loader to see the class it CHECKCASTs to. Sharing one
     * loader is what removed that chain — and the caller still has to resolve the Glob.
     */
    @Test
    public void aCallerResolvesTheGlobItReadsWithoutAChildLoader() {
        GlobType type = generated("LoaderCaller", 4);
        MutableGlob glob = type.instantiate();
        glob.setValue(type.getFields()[0], "a value");

        List<String> seen = new java.util.ArrayList<>();
        ((CallerGlobFactory) type.getGlobFactory()).create("loader.read", recorder()).call(glob, seen, null);

        Assertions.assertEquals(type.getFieldCount(), seen.size());
        Assertions.assertSame(glob.getClass().getClassLoader(), GeneratedClassLoader.get());
    }

    /**
     * Two GlobTypes of the same name and the same layout have the same family key, and cannot share a class :
     * a generated factory holds a static TYPE bound to one type instance. In one shared loader that would be
     * a duplicate class definition — the suffix GeneratedName adds to a key already in use is what makes the
     * shared loader possible at all, and this is the test that would fail as a LinkageError without it.
     */
    @Test
    public void twoTypesWithTheSameKeyGetTwoClassesInTheOneLoader() {
        GlobType first = generated("LoaderTwin", 4);
        GlobType second = generated("LoaderTwin", 4);

        Class<?> firstGlob = first.instantiate().getClass();
        Class<?> secondGlob = second.instantiate().getClass();

        Assertions.assertNotSame(firstGlob, secondGlob);
        Assertions.assertSame(firstGlob.getClassLoader(), secondGlob.getClassLoader());
        Assertions.assertEquals(secondGlob.getName(), firstGlob.getName() + "_1");
        // the Glob answers its factory's static TYPE : sharing one class would answer the wrong type here
        Assertions.assertSame(first, first.instantiate().getType(), "each factory wired to its own type");
        Assertions.assertSame(second, second.instantiate().getType());
    }

    /** The check that the naming scheme is doing its job : one name, one class, or a loud failure. */
    @Test
    public void twoGenerationsAgreeingOnAClassNameIsRefused() {
        GeneratedClassLoader loader = GeneratedClassLoader.get();
        loader.emit("org/globsframework/gen/test/Taken", () -> new byte[0]);
        Assertions.assertThrows(IllegalStateException.class,
                () -> loader.emit("org/globsframework/gen/test/Taken", () -> new byte[0]));
    }

    private GlobType generated(String name, int count) {
        System.setProperty("globs.builder",
                "org.globsframework.model.generator.object.GeneratorGlobFactoryService");
        GlobFactoryService.Builder.reset();
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create(name);
        for (int i = 0; i < count; i++) {
            builder.declareStringField("s" + i);
        }
        GlobType type = builder.build();
        Assertions.assertInstanceOf(CallerGlobFactory.class, type.getGlobFactory(),
                "nothing was generated : the rest of this test would be vacuous");
        return type;
    }

    private static SortedMap<Integer, ToGlobFunction<Void, Void, Void>> writeFunctions() {
        SortedMap<Integer, ToGlobFunction<Void, Void, Void>> functions = new TreeMap<>();
        functions.put(1, (MutableGlob data, Void c1, Void c2, Void c3) -> {
        });
        return functions;
    }

    private static FromGlobCallerFactory.Functions<List<String>, Void> recorder() {
        return new FromGlobCallerFactory.Functions<>() {
            public <T> FromGlobFunction<T, List<String>, Void> forField(Field field) {
                String name = field.getName();
                return (isSet, isNull, value, ctx1, ctx2) -> ctx1.add(name + "=" + value);
            }
        };
    }
}
