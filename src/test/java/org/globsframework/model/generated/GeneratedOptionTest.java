package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.GlobFactoryService;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.model.generator.GenerationOption;
import org.globsframework.model.generator.annotations.GeneratedOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The GeneratedOption annotation, and the system property defaults behind it.
 * <p>
 * Everything here fails silently by nature : ignoring the annotation just means the default applies and the
 * glob still works. So every assertion is on the *class* of what was built, never on behaviour alone.
 */
public class GeneratedOptionTest {
    private static final String OBJECT_SERVICE = "org.globsframework.model.generator.object.GeneratorGlobFactoryService";
    private static final String PRIMITIVE_SERVICE = "org.globsframework.model.generator.primitive.GeneratorGlobFactoryService";

    @AfterEach
    public void tearDown() {
        System.clearProperty("globs.builder");
        System.clearProperty(GenerationOption.MODE_PROPERTY);
        System.clearProperty(GenerationOption.ACCESSORS_PROPERTY);
        GlobFactoryService.Builder.reset();
    }

    private GlobType build(String service, String name, Glob... annotations) {
        System.setProperty("globs.builder", service);
        GlobFactoryService.Builder.reset();
        GlobTypeBuilder builder = GlobTypeBuilderFactory.create(name);
        for (Glob annotation : annotations) {
            builder.addAnnotation(annotation);
        }
        builder.declareStringField("s");
        builder.declareIntegerField("i");
        builder.declareDoubleField("d");
        return builder.build();
    }

    private String superclassOf(GlobType type) {
        return type.instantiate().getClass().getSuperclass().getName();
    }

    private boolean accessorsAreGenerated(GlobType type) {
        Field field = type.getField("i");
        return type.getGlobFactory().getGetValueAccessor(field).getClass().getName()
                .startsWith("org.globsframework.gen.");
    }

    @Test
    public void noneMeansCoreDefaultGlobEvenWhenAServiceIsInstalled() {
        GlobType type = build(OBJECT_SERVICE, "OptNone", GeneratedOption.none());
        Assertions.assertTrue(type.instantiate().getClass().getName()
                        .startsWith("org.globsframework.core.model.impl.DefaultGlob"),
                "expected core's glob but got " + type.instantiate().getClass().getName());
    }

    @Test
    public void theAnnotationBeatsTheInstalledService() {
        Assertions.assertEquals("org.globsframework.model.generator.primitive.AbstractGeneratedGlob32",
                superclassOf(build(OBJECT_SERVICE, "OptPrimOverObj", GeneratedOption.primitive())));
        Assertions.assertEquals("org.globsframework.model.generator.object.AbstractGeneratedGlob32",
                superclassOf(build(PRIMITIVE_SERVICE, "OptObjOverPrim", GeneratedOption.object())));
    }

    @Test
    public void theServiceIsTheDefaultWhenTheTypeSaysNothing() {
        Assertions.assertEquals("org.globsframework.model.generator.object.AbstractGeneratedGlob32",
                superclassOf(build(OBJECT_SERVICE, "OptPlainObj")));
        Assertions.assertEquals("org.globsframework.model.generator.primitive.AbstractGeneratedGlob32",
                superclassOf(build(PRIMITIVE_SERVICE, "OptPlainPrim")));
    }

    @Test
    public void accessorsCanBeTurnedOffWithoutTurningOffTheGlob() {
        GlobType type = build(PRIMITIVE_SERVICE, "OptNoAcc", GeneratedOption.withAccessors(false));
        Assertions.assertEquals("org.globsframework.model.generator.primitive.AbstractGeneratedGlob32",
                superclassOf(type), "the Glob itself must still be generated");
        Assertions.assertFalse(accessorsAreGenerated(type));

        GlobType with = build(PRIMITIVE_SERVICE, "OptAcc", GeneratedOption.primitive(true));
        Assertions.assertTrue(accessorsAreGenerated(with));
    }

    /** The doGet-based accessors have to keep working against a generated Glob, which is a new pairing. */
    @Test
    public void theNonGeneratedAccessorsStillRoundTripOnAGeneratedGlob() {
        for (String service : new String[]{OBJECT_SERVICE, PRIMITIVE_SERVICE}) {
            GlobType type = build(service, "OptRoundTrip" + service.hashCode(), GeneratedOption.withAccessors(false));
            MutableGlob glob = type.instantiate();
            StringField s = type.getField("s").asStringField();
            IntegerField i = type.getField("i").asIntegerField();

            Assertions.assertFalse(accessorsAreGenerated(type));
            Assertions.assertFalse(type.getGlobFactory().getGetValueAccessor(i).isSet(glob));
            Assertions.assertTrue(type.getGlobFactory().getGetValueAccessor(i).isNull(glob));

            type.getGlobFactory().getSetValueAccessor(i).setValue(glob, 42);
            type.getGlobFactory().getSetValueAccessor(s).setValue(glob, "x");
            Assertions.assertEquals(42, type.getGlobFactory().getGetValueAccessor(i).getValue(glob));
            Assertions.assertEquals("x", type.getGlobFactory().getGetValueAccessor(s).getValue(glob));
            Assertions.assertTrue(type.getGlobFactory().getGetValueAccessor(i).isSet(glob));
            Assertions.assertFalse(type.getGlobFactory().getGetValueAccessor(i).isNull(glob));

            type.getGlobFactory().getSetValueAccessor(i).setValue(glob, null);
            Assertions.assertTrue(type.getGlobFactory().getGetValueAccessor(i).isNull(glob));
            Assertions.assertTrue(type.getGlobFactory().getGetValueAccessor(i).isSet(glob));
        }
    }

    @Test
    public void thePropertiesAreTheDefaultAndTheAnnotationOverridesThem() {
        System.setProperty(GenerationOption.MODE_PROPERTY, GeneratedOption.NONE);
        Assertions.assertTrue(build(OBJECT_SERVICE, "OptPropNone").instantiate().getClass().getName()
                .startsWith("org.globsframework.core.model.impl.DefaultGlob"));
        Assertions.assertEquals("org.globsframework.model.generator.primitive.AbstractGeneratedGlob32",
                superclassOf(build(OBJECT_SERVICE, "OptPropNoneButAnnotated", GeneratedOption.primitive())),
                "the annotation must win over " + GenerationOption.MODE_PROPERTY);

        System.clearProperty(GenerationOption.MODE_PROPERTY);
        System.setProperty(GenerationOption.ACCESSORS_PROPERTY, "false");
        Assertions.assertFalse(accessorsAreGenerated(build(OBJECT_SERVICE, "OptPropNoAcc")));
        Assertions.assertTrue(accessorsAreGenerated(build(OBJECT_SERVICE, "OptPropAnnotated",
                        GeneratedOption.object(true))),
                "the annotation must win over " + GenerationOption.ACCESSORS_PROPERTY);
    }

    @Test
    public void anUnknownModeFailsLoudly() {
        System.setProperty(GenerationOption.MODE_PROPERTY, "primitiv");
        RuntimeException e = Assertions.assertThrows(RuntimeException.class,
                () -> build(OBJECT_SERVICE, "OptTypo"));
        Assertions.assertTrue(rootCause(e) instanceof IllegalArgumentException,
                "expected an IllegalArgumentException but got " + rootCause(e));
        Assertions.assertTrue(rootCause(e).getMessage().contains("primitiv"), rootCause(e).getMessage());
    }

    private Throwable rootCause(Throwable e) {
        return e.getCause() == null ? e : rootCause(e.getCause());
    }
}
