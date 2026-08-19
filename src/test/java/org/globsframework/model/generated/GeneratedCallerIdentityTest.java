package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.generate.read.FieldValueFunction;
import org.globsframework.core.model.generate.read.GenerateCaller;
import org.globsframework.core.model.generate.write.CallAtWrite;
import org.globsframework.core.model.generate.write.DefaultFunctionCallerWrite;
import org.globsframework.core.model.generate.read.GlobGenerateFactory;
import org.globsframework.core.model.generate.write.MutableFunctionWrite;
import org.globsframework.model.generator.AsmCallerGenerator;
import org.globsframework.model.generator.AsmCallerWriteGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * A generated caller is a class the JVM has to be able to recognise from one run of the application to the
 * next — that is what an AOT cache matches on, and a name taken from a counter matches nothing. So the name
 * is built out of the purpose the codec gives and a digest of what is being generated over, and this is what
 * holds it to that.
 * <p>
 * The check that means something is a forked one : the same names have to come out of a second JVM, and out
 * of one that built other callers first. Everything that could make a name drift — a global counter, a
 * HashMap iteration order, an identity hash — survives an in-process assertion and dies here.
 * <p>
 * The second fork covers the families the Glob generators emit — the Glob, its factory, its accessors — and
 * a caller over one of them, which is what could only become reproducible once they were.
 */
public class GeneratedCallerIdentityTest {

    /**
     * Runs in the forked JVM. With an argument, builds unrelated callers first : a name that depended on
     * how many callers came before it would shift here, and nothing else would notice.
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("globs")) {
            // -Dglobs.builder is set : the type gets a generated Glob, and everything hangs off its family
            GlobType type = declare("org.globsframework.model.generated.Family", 6);
            Field field = type.getFields()[3];
            System.out.println(type.instantiate().getClass().getName());
            System.out.println(type.getGlobFactory().getClass().getName());
            System.out.println(type.getGlobFactory().getGetValueAccessor(field).getClass().getName());
            System.out.println(type.getGlobFactory().getSetValueAccessor(field).getClass().getName());
            System.out.println(((GlobGenerateFactory) type.getGlobFactory())
                    .create("binser.write", recorder()).getClass().getName());
            return;
        }
        if (args.length > 0) {
            for (int i = 0; i < 5; i++) {
                AsmCallerWriteGenerator.INSTANCE.create("decoy" + i, functions(3, 4), null, -1);
                AsmCallerGenerator.forDefaultGlob(declare("Decoy" + i, 6)).create("decoy" + i, recorder());
            }
        }
        System.out.println(AsmCallerWriteGenerator.INSTANCE
                .create("binser.read", functions(1, 2, 5), null, -1).getClass().getName());
        System.out.println(AsmCallerWriteGenerator.INSTANCE
                .create("binser.readAll", new MutableFunctionWrite[]{write(), write(), write()})
                .getClass().getName());
        System.out.println(AsmCallerGenerator.forDefaultGlob(declare("Identity", 20))
                .create("binser.write", recorder()).getClass().getName());
    }

    @Test
    public void theSameCallersGetTheSameClassNamesInAnotherJvm() throws Exception {
        List<String> first = runForked();
        Assertions.assertEquals(3, first.size(), String.join("\n", first));
        Assertions.assertEquals(first, runForked());
        Assertions.assertEquals(first, runForked("with-decoys"));
    }

    /**
     * The other half of the same invariant, on the classes the Glob generators emit : the Glob, the factory
     * and the accessors of one type are one <em>family</em>, named from one key, and a caller over that Glob
     * names the Glob class — so none of them could be reproducible until the family was.
     */
    @Test
    public void aGeneratedGlobAndEverythingHangingOffItGetTheSameNamesInAnotherJvm() throws Exception {
        for (String flavour : new String[]{"object", "primitive"}) {
            List<String> first = runForked(
                    List.of("-Dglobs.builder=org.globsframework.model.generator." + flavour
                            + ".GeneratorGlobFactoryService"),
                    List.of("globs"));
            Assertions.assertEquals(5, first.size(), String.join("\n", first));
            Assertions.assertTrue(first.get(0).contains("Glob_Family_"), first.get(0));
            Assertions.assertTrue(first.get(1).contains("Factory_Family_"), first.get(1));
            Assertions.assertTrue(first.get(2).contains("Get_Family_"), first.get(2));
            Assertions.assertTrue(first.get(3).contains("Set_Family_"), first.get(3));
            Assertions.assertEquals(first, runForked(
                    List.of("-Dglobs.builder=org.globsframework.model.generator." + flavour
                            + ".GeneratorGlobFactoryService"),
                    List.of("globs")), flavour);
        }
    }

    /** The purpose is what a stack trace and a profile show, so it has to survive into the name. */
    @Test
    public void theNameTheCallerGaveIsInTheClassName() {
        Assertions.assertTrue(AsmCallerWriteGenerator.INSTANCE
                        .create("myFormat.read", functions(1), null, -1).getClass().getName()
                        .contains("myFormat_read"),
                "the purpose is what makes the name readable");
        Assertions.assertTrue(AsmCallerGenerator.forDefaultGlob(declare("Named", 4))
                        .create("myFormat.write", recorder()).getClass().getName()
                        .contains("myFormat_write_Named"));
    }

    /**
     * A GlobType is usually named after a fully qualified class, and the purpose can be long — so the two
     * are capped on their own. Cutting the pair as one string dropped the type, i.e. exactly the end that
     * says which caller this is, and left a hundred characters that no longer told them apart.
     */
    @Test
    public void aLongPurposeDoesNotEatTheTypeNameNextToIt() {
        GlobType type = declare("org.globsframework.serialisation.model.DummyObject", 4);
        String name = name(AsmCallerGenerator.forDefaultGlob(type)
                .create("binser.write.the.whole.pipeline", recorder()));

        Assertions.assertTrue(name.contains("DummyObject"), name);
        Assertions.assertTrue(name.contains("binser_write"), name);
        Assertions.assertFalse(name.contains("globsframework_serialisation"),
                "the package of the type is in the digest, not in the name : " + name);
    }

    /**
     * The digest covers everything the emitted bytes depend on, so a change of shape under one purpose is a
     * different name — never the same name with different bytes, which is the one thing a cache could not
     * survive.
     */
    @Test
    public void adifferentShapeUnderTheSamePurposeIsADifferentName() {
        String keys = name(AsmCallerWriteGenerator.INSTANCE.create("shape", functions(1, 2), null, -1));
        String otherKeys = name(AsmCallerWriteGenerator.INSTANCE.create("shape", functions(1, 3), null, -1));
        String fallback = name(AsmCallerWriteGenerator.INSTANCE.create("shape", functions(1, 2), write(), -1));
        String endLoop = name(AsmCallerWriteGenerator.INSTANCE.create("shape", functions(1, 2), null, -2));

        Assertions.assertNotEquals(keys, otherKeys);
        Assertions.assertNotEquals(keys, fallback);
        Assertions.assertNotEquals(keys, endLoop);

        GlobType four = declare("Shaped", 4);
        Assertions.assertNotEquals(
                name(AsmCallerGenerator.forDefaultGlob(four).create("shape", recorder())),
                name(AsmCallerGenerator.forDefaultGlob(declare("Shaped", 5)).create("shape", recorder())),
                "same purpose, same type name, one field more");
    }

    /**
     * One purpose asked twice for the same shape is a codec built twice — two factories in one process. It
     * still needs two classes, so the second is suffixed : the first one keeps the reproducible name, and
     * the duplicates are what pays for the collision rather than everybody.
     */
    @Test
    public void thesamePurposeAskedTwiceKeepsTheFirstNameAndSuffixesTheOthers() {
        String first = name(AsmCallerWriteGenerator.INSTANCE.create("twice", functions(7), null, -1));
        String second = name(AsmCallerWriteGenerator.INSTANCE.create("twice", functions(7), null, -1));

        Assertions.assertEquals(first + "_1", second);
    }

    /** Ignored by the loop, but refused by it too : a name has to be there before a JVM starts generating. */
    @Test
    public void aCallerWithoutANameIsRefusedByTheGeneratorAndByTheLoop() {
        for (var factory : List.of(AsmCallerWriteGenerator.INSTANCE, DefaultFunctionCallerWrite.INSTANCE)) {
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> factory.create(null, functions(1), null, -1), factory.getClass().getName());
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> factory.create(" ", functions(1), null, -1), factory.getClass().getName());
        }
        GlobType type = declare("Unnamed", 4);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AsmCallerGenerator.forDefaultGlob(type).create(null, recorder()));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> GenerateCaller.callerFor(null, type, recorder()));
    }

    private static String name(Object caller) {
        return caller.getClass().getName();
    }

    private List<String> runForked(String... args) throws Exception {
        return runForked(List.of(), List.of(args));
    }

    private List<String> runForked(List<String> vmOptions, List<String> args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path")));
        command.addAll(vmOptions);
        command.add(GeneratedCallerIdentityTest.class.getName());
        command.addAll(args);

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }
        Assertions.assertTrue(process.waitFor(2, TimeUnit.MINUTES), "the forked JVM did not finish");
        Assertions.assertEquals(0, process.exitValue(), String.join("\n", output));
        return output;
    }

    private static GlobType declare(String name, int count) {
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create(name);
        for (int i = 0; i < count; i++) {
            builder.declareStringField("s" + i);
        }
        return builder.build();
    }

    private static SortedMap<Integer, MutableFunctionWrite<Void, Void, Void>> functions(int... keys) {
        SortedMap<Integer, MutableFunctionWrite<Void, Void, Void>> functions = new TreeMap<>();
        for (int key : keys) {
            functions.put(key, write());
        }
        return functions;
    }

    private static MutableFunctionWrite<Void, Void, Void> write() {
        return (MutableGlob data, Void ctx1, Void ctx2, Void ctx3) -> {
        };
    }

    private static GenerateCaller.GetFieldValueFunction<List<String>, Void> recorder() {
        return new GenerateCaller.GetFieldValueFunction<>() {
            public <T> FieldValueFunction<T, List<String>, Void> create(Field field) {
                return (isSet, isNull, value, ctx1, ctx2) -> {
                };
            }
        };
    }
}
