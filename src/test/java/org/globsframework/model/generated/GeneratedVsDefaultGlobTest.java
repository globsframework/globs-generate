package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.FieldValues;
import org.globsframework.core.model.GlobFactoryService;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.serialization.ByteBufferSerializationOutput;
import org.globsframework.core.utils.serialization.GlobSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Differential test : a generated Glob must be observationally identical to core's DefaultGlob.
 * <p>
 * The same scripted scenario runs on core, on the object flavour and on the primitive flavour, each step
 * recording what it observed as a line of text; the recordings are then compared line by line, so a
 * divergence points at the exact operation rather than at a generic assertion failure.
 */
public class GeneratedVsDefaultGlobTest {
    private static final String OBJECT = "org.globsframework.model.generator.object.GeneratorGlobFactoryService";
    private static final String PRIMITIVE = "org.globsframework.model.generator.primitive.GeneratorGlobFactoryService";
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @AfterEach
    public void tearDown() {
        System.clearProperty("globs.builder");
        GlobFactoryService.Builder.reset();
    }

    @Test
    public void objectFlavourIsIsoWithDefaultGlob() {
        assertIso(OBJECT, 9);   // AbstractGeneratedGlob32
        assertIso(OBJECT, 45);  // AbstractGeneratedGlob64
    }

    @Test
    public void primitiveFlavourIsIsoWithDefaultGlob() {
        assertIso(PRIMITIVE, 9);
        assertIso(PRIMITIVE, 45);
    }

    private void assertIso(String service, int fieldCount) {
        List<String> expected = record(null, fieldCount);
        List<String> actual = record(service, fieldCount);

        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            Assertions.assertEquals(expected.get(i), actual.get(i),
                    "DefaultGlob and " + service + " diverge with " + fieldCount + " fields, at step " + i);
        }
        Assertions.assertEquals(expected.size(), actual.size(),
                "different number of observations for " + service);
    }

    /** Runs the scenario and returns one line per observation. */
    private List<String> record(String service, int fieldCount) {
        if (service == null) {
            System.clearProperty("globs.builder");
        } else {
            System.setProperty("globs.builder", service);
        }
        GlobFactoryService.Builder.reset();

        GlobType type = buildType("Iso" + UNIQUE.incrementAndGet(), fieldCount);
        MutableGlob glob = type.instantiate();
        Field[] fields = type.getFields();
        List<String> lines = new ArrayList<>();

        if (service != null) {
            // guards the guard : an inert generator would make every comparison trivially true
            Assertions.assertTrue(glob.getClass().getName().startsWith("org.globsframework.gen."),
                    "not a generated glob : " + glob.getClass().getName());
        }

        state(lines, "fresh", glob, fields);

        for (Field field : fields) {
            glob.setValue(field, valueFor(field));
        }
        state(lines, "afterSet", glob, fields);
        visitors(lines, "afterSet", glob);
        lines.add("toString=" + glob.toString());
        lines.add("serialized=" + serialize(glob));

        MutableGlob duplicate = glob.duplicate();
        lines.add("duplicate.matches=" + glob.matches(duplicate));
        lines.add("duplicate.equals=" + glob.equals(duplicate));
        state(lines, "duplicate", duplicate, fields);
        visitors(lines, "duplicate", duplicate);

        glob.unset(fields[0]);
        glob.unset(fields[fields.length - 1]);
        state(lines, "afterUnset", glob, fields);
        visitors(lines, "afterUnset", glob);
        lines.add("afterUnset.toString=" + glob.toString());

        glob.setValue(fields[1], null);
        state(lines, "afterSetNull", glob, fields);
        visitors(lines, "afterSetNull", glob);
        lines.add("afterSetNull.toString=" + glob.toString());
        lines.add("afterSetNull.matchesDuplicate=" + glob.matches(duplicate));

        return normalise(lines, type.getName());
    }

    /**
     * Each recording needs its own GlobType, so the type name differs by construction; and toString falls
     * back to Object.toString for byte[], whose identity hash differs per instance. Neither says anything
     * about the implementations.
     */
    private List<String> normalise(List<String> lines, String typeName) {
        return lines.stream()
                .map(line -> line.replace(typeName, "TYPE").replaceAll("@[0-9a-f]+", "@ID"))
                .toList();
    }

    private void state(List<String> lines, String step, MutableGlob glob, Field[] fields) {
        for (Field field : fields) {
            lines.add(step + "." + field.getName()
                      + " isSet=" + glob.isSet(field)
                      + " isNull=" + glob.isNull(field)
                      + " value=" + format(glob.getValue(field)));
        }
    }

    /** The three traversals must agree on which fields are visited, and with which values. */
    private void visitors(List<String> lines, String step, MutableGlob glob) {
        StringBuilder accept = new StringBuilder();
        glob.safeAccept(new FieldValueVisitor.AbstractFieldValueVisitor() {
            public void notManaged(Field field, Object value) {
                accept.append(field.getName()).append('=').append(format(value)).append(';');
            }
        });
        lines.add(step + ".accept=" + accept);

        StringBuilder withContext = new StringBuilder();
        glob.safeAccept(new FieldValueVisitorWithContext.AbstractFieldValueVisitor<StringBuilder>() {
            public void notManaged(Field field, Object value, StringBuilder ctx) {
                ctx.append(field.getName()).append('=').append(format(value)).append(';');
            }
        }, withContext);
        lines.add(step + ".acceptWithContext=" + withContext);

        StringBuilder apply = new StringBuilder();
        glob.safeApply((FieldValues.Functor) (field, value) ->
                apply.append(field.getName()).append('=').append(format(value)).append(';'));
        lines.add(step + ".apply=" + apply);
    }

    private String serialize(MutableGlob glob) {
        ByteBufferSerializationOutput output = new ByteBufferSerializationOutput(new byte[64 * 1024]);
        new GlobSerializer(output).writeKnowGlob(glob);
        return HexFormat.of().formatHex(Arrays.copyOf(output.getBuffer(), output.position()));
    }

    private static String format(Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            return value instanceof Object[] o ? Arrays.deepToString(o)
                    : value instanceof int[] i ? Arrays.toString(i)
                    : value instanceof long[] l ? Arrays.toString(l)
                    : value instanceof double[] d ? Arrays.toString(d)
                    : value instanceof boolean[] b ? Arrays.toString(b)
                    : value instanceof byte[] by ? Arrays.toString(by)
                    : value.toString();
        }
        return value.toString();
    }

    /** Cycles through every kind of field so the comparison is not limited to the easy ones. */
    private GlobType buildType(String name, int fieldCount) {
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create(name);
        for (int i = 0; i < fieldCount; i++) {
            switch (i % 13) {
                case 0 -> builder.declareStringField("str" + i);
                case 1 -> builder.declareIntegerField("int" + i);
                case 2 -> builder.declareDoubleField("dbl" + i);
                case 3 -> builder.declareLongField("lng" + i);
                case 4 -> builder.declareBooleanField("bool" + i);
                case 5 -> builder.declareBigDecimalField("dec" + i);
                case 6 -> builder.declareDateField("date" + i);
                case 7 -> builder.declareDateTimeField("dttm" + i);
                case 8 -> builder.declareBytesField("bytes" + i);
                case 9 -> builder.declareIntegerArrayField("intArr" + i);
                case 10 -> builder.declareStringArrayField("strArr" + i);
                case 11 -> builder.declareDoubleArrayField("dblArr" + i);
                default -> builder.declareBooleanArrayField("boolArr" + i);
            }
        }
        return builder.build();
    }

    private Object valueFor(Field field) {
        int i = field.getIndex();
        if (field instanceof StringField) return "v" + i;
        if (field instanceof IntegerField) return i;
        if (field instanceof DoubleField) return i + 0.5;
        if (field instanceof LongField) return (long) i * 1000;
        if (field instanceof BooleanField) return i % 2 == 0;
        if (field instanceof BigDecimalField) return new BigDecimal(i + ".25");
        if (field instanceof DateField) return LocalDate.of(2026, 8, 1 + i % 27);
        if (field instanceof DateTimeField) return ZonedDateTime.of(2026, 8, 2, i % 24, 0, 0, 0, ZoneId.of("UTC"));
        if (field instanceof BytesField) return new byte[]{(byte) i, (byte) (i + 1)};
        if (field instanceof IntegerArrayField) return new int[]{i, i + 1};
        if (field instanceof StringArrayField) return new String[]{"a" + i, "b" + i};
        if (field instanceof DoubleArrayField) return new double[]{i + 0.5, i + 1.5};
        if (field instanceof BooleanArrayField) return new boolean[]{true, false};
        throw new IllegalStateException("unexpected field " + field);
    }
}
