package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Everything else in this suite installs the service from inside a test method, by which point half the
 * framework's classes are already initialized. The configuration the module actually ships in is
 * {@code -Dglobs.builder=...} on the command line, where the very first GlobType built in the JVM — one of
 * core's own annotation types, during their mutual bootstrap — is the one that goes through
 * GenerationOption.resolve.
 * <p>
 * That is the window where anything the resolution touches gets initialized at the worst possible moment,
 * and it cannot be reproduced in-process because classes do not unload. Hence a real JVM.
 */
public class GeneratedFactoryBootstrapTest {

    /** Run in the forked JVM: build a type before anything else has, and print what came out. */
    public static void main(String[] args) {
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create("BootstrapFirstType");
        builder.declareIntegerField("i");
        builder.declareStringField("s");
        GlobType type = builder.build();
        System.out.println("SUPERCLASS=" + type.instantiate().getClass().getSuperclass().getName());
    }

    @Test
    public void theFirstTypeOfTheJvmIsGeneratedWhenTheServiceComesFromTheCommandLine() throws Exception {
        assertBootstraps("org.globsframework.model.generator.object.GeneratorGlobFactoryService",
                "org.globsframework.model.generator.object.AbstractGeneratedGlob32");
        assertBootstraps("org.globsframework.model.generator.primitive.GeneratorGlobFactoryService",
                "org.globsframework.model.generator.primitive.AbstractGeneratedGlob32");
    }

    private void assertBootstraps(String service, String expectedSuperclass) throws Exception {
        List<String> command = List.of(
                System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path"),
                "-Dglobs.builder=" + service,
                GeneratedFactoryBootstrapTest.class.getName());

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

        String joined = String.join("\n", output);
        Assertions.assertEquals(0, process.exitValue(),
                "building the first GlobType of a JVM started with " + service + " failed :\n" + joined);
        Assertions.assertTrue(joined.contains("SUPERCLASS=" + expectedSuperclass),
                "expected " + expectedSuperclass + " but got :\n" + joined);
    }
}
