package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.LoopFromGlobCaller;
import org.globsframework.core.model.caller.FromGlobFunction;
import org.globsframework.core.model.caller.FromGlobCallerFactory;
import org.globsframework.core.model.caller.FromGlobCaller;
import org.globsframework.model.generator.AsmCallerGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AsmCallerGenerator.forDefaultGlob reads the set mask the way each concrete class writes it — an int for
 * DefaultGlob32, a long for DefaultGlob64, one of two longs (shifted by index-64) for DefaultGlob128, a
 * BitSet above that. A wrong shift is a wrong answer for one field, silently.
 * <p>
 * {@link GeneratedFromGlobCallerTest} covers the last three. The first one is unreachable in a normal JVM :
 * DefaultGlobFactory picks DefaultGlob32 only when {@code gfw.minSize <= 32}, and that is a static final read
 * once from a system property. Hence a fork — and since the fork costs the same for one shape or four, it
 * checks all four, which also pins the class DefaultGlobFactory picks at each size.
 */
public class DefaultGlobCallerShapesTest {

    /** Runs in the forked JVM : per size, the concrete Glob class and whether the two callers agree. */
    public static void main(String[] args) {
        for (int count : new int[]{20, 45, 100, 200}) {
            GlobType type = declare("Shape" + count, count);
            MutableGlob glob = fill(type, count);
            List<String> generated = trace(AsmCallerGenerator.forDefaultGlob(type).create("shapes", recorder()), glob);
            List<String> looped = trace(new LoopFromGlobCaller<>(type, recorder()), glob);
            System.out.println(count + " " + type.instantiate().getClass().getSimpleName()
                               + " " + (generated.equals(looped) ? "OK" : "MISMATCH " + firstDiff(looped, generated)));
        }
    }

    @Test
    public void everyMaskShapeAgreesWithTheLoopedCaller() throws Exception {
        Assertions.assertEquals(
                List.of("20 DefaultGlob32 OK",
                        "45 DefaultGlob64 OK",
                        "100 DefaultGlob128 OK",
                        "200 DefaultGlob OK"),
                runForked("-Dgfw.minSize=32"));
    }

    private List<String> runForked(String... options) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path")));
        command.addAll(List.of(options));
        command.add(DefaultGlobCallerShapesTest.class.getName());

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
            switch (i % 4) {
                case 0 -> builder.declareStringField("s" + i);
                case 1 -> builder.declareIntegerField("i" + i);
                case 2 -> builder.declareDoubleField("d" + i);
                default -> builder.declareLongField("l" + i);
            }
        }
        return builder.build();
    }

    /** every third field set to a value, every third to an explicit null, the rest untouched */
    private static MutableGlob fill(GlobType type, int count) {
        MutableGlob glob = type.instantiate();
        Field[] fields = type.getFields();
        for (int i = 0; i < count; i++) {
            switch (i % 3) {
                case 0 -> glob.setValue(fields[i], null);
                case 1 -> glob.setValue(fields[i], sample(fields[i]));
                default -> {
                }
            }
        }
        return glob;
    }

    private static Object sample(Field field) {
        return switch (field.getName().charAt(0)) {
            case 's' -> "v" + field.getIndex();
            case 'i' -> field.getIndex();
            case 'd' -> field.getIndex() + 0.5;
            default -> (long) field.getIndex();
        };
    }

    private static FromGlobCallerFactory.Functions<List<String>, Void> recorder() {
        return new FromGlobCallerFactory.Functions<>() {
            public <T> FromGlobFunction<T, List<String>, Void> forField(Field field) {
                String name = field.getName();
                return (isSet, isNull, value, ctx1, ctx2) ->
                        ctx1.add(name + "|" + isSet + "|" + isNull + "|" + value);
            }
        };
    }

    private static List<String> trace(FromGlobCaller<List<String>, Void> caller, MutableGlob glob) {
        List<String> seen = new ArrayList<>();
        caller.call(glob, seen, null);
        return seen;
    }

    private static String firstDiff(List<String> expected, List<String> actual) {
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                return "at " + i + " expected " + expected.get(i) + " got " + actual.get(i);
            }
        }
        return "sizes " + expected.size() + " vs " + actual.size();
    }
}
