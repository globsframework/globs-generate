package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.model.generator.annotations.GeneratedOption;

/**
 * What is actually generated for one GlobType, once the {@link GeneratedOption} annotation and the system
 * property defaults have been merged.
 * <p>
 * Resolution, per setting and independently for each of the two : the annotation if it *sets* that field,
 * otherwise the system property, otherwise the built-in default. A GeneratedOption that only sets the mode
 * therefore still takes its accessors setting from the deployment, which is the point of keeping unset and
 * false distinct here.
 * <p>
 * Both properties are read at each resolution rather than cached in a static, so a test or a benchmark can
 * change them between two types without a reset dance. A type is built rarely enough for that to be free.
 */
public record GenerationOption(Mode mode, boolean accessors) {

    public enum Mode {
        /** No generation at all : core's DefaultGlob, as if the module were not on the classpath. */
        NONE,
        /** Values in boxed fields, null is the reference itself. */
        OBJECT,
        /** Values in native fields, null in a separate mask. */
        PRIMITIVE
    }

    /** none | object | primitive — the default flavour when a type says nothing. */
    public static final String MODE_PROPERTY = "globs.generate.mode";

    /** true | false — whether to generate the per-field accessor classes when a type says nothing. */
    public static final String ACCESSORS_PROPERTY = "globs.generate.accessors";

    /**
     * @param serviceDefault the flavour of the GlobFactoryService that was selected through globs.builder :
     *                       what a type gets when neither its annotation nor the property says otherwise.
     */
    public static GenerationOption resolve(GlobType type, Mode serviceDefault) {
        Glob annotation = findOption(type);
        return new GenerationOption(resolveMode(annotation, serviceDefault), resolveAccessors(annotation));
    }

    /**
     * Matched on the type *name*, not through {@code GeneratedOption.UNIQUE_KEY}, and the field names are
     * compile-time String constants so that naming them does not load the GeneratedOption class either.
     * <p>
     * The reason is the stack this runs on: DefaultGlobType's constructor. Reading
     * {@code GeneratedOption.UNIQUE_KEY} would run that class's static initializer, which builds a GlobType
     * — a whole type construction nested inside another one, and re-entering this very method for the
     * annotation type itself, at a point where its own UNIQUE_KEY is still null. Measured, it happens to
     * work today: the re-entrant call reads a null Key and findAnnotation(null) answers null, which is the
     * right answer for that type. It works by accident, on two behaviours nobody promised, on the path that
     * every single GlobType construction in the JVM goes through, and it fails as an
     * ExceptionInInitializerError at startup. The name lookup costs one String comparison per annotation
     * and owes nothing to class-initialization order.
     * <p>
     * Rule for anything reachable from getFactory: build no GlobType, initialize no annotation class.
     * {@code GeneratedFactoryBootstrapTest} starts a JVM with globs.builder set — the deployment
     * configuration, and the only place this class of breakage shows up — and checks the first type built
     * comes out generated. To be clear about what that does and does not prove: the UNIQUE_KEY form passes
     * it too today. It guards the rule, not the choice.
     */
    private static Glob findOption(GlobType type) {
        return type.streamAnnotations()
                .filter(annotation -> annotation.getType().getName().equals(GeneratedOption.TYPE_NAME))
                .findFirst()
                .orElse(null);
    }

    private static Mode resolveMode(Glob annotation, Mode serviceDefault) {
        Object mode = valueOf(annotation, GeneratedOption.MODE_NAME);
        if (mode != null) {
            return parseMode((String) mode, "the " + GeneratedOption.MODE_NAME + " of the annotation");
        }
        String property = System.getProperty(MODE_PROPERTY);
        if (property != null) {
            return parseMode(property, "system property " + MODE_PROPERTY);
        }
        return serviceDefault;
    }

    private static boolean resolveAccessors(Glob annotation) {
        Object accessors = valueOf(annotation, GeneratedOption.ACCESSORS_NAME);
        if (accessors != null) {
            return Boolean.TRUE.equals(accessors);
        }
        return Boolean.parseBoolean(System.getProperty(ACCESSORS_PROPERTY, "true"));
    }

    /** null for "the annotation is absent, or leaves this one unset", which is what falls back to the default. */
    private static Object valueOf(Glob annotation, String fieldName) {
        if (annotation == null) {
            return null;
        }
        Field field = annotation.getType().findField(fieldName);
        return field != null && annotation.isSet(field) ? annotation.getValue(field) : null;
    }

    /** Unknown values throw rather than falling back : a typo here silently costs the whole optimisation. */
    private static Mode parseMode(String value, String origin) {
        return switch (value) {
            case GeneratedOption.NONE -> Mode.NONE;
            case GeneratedOption.OBJECT -> Mode.OBJECT;
            case GeneratedOption.PRIMITIVE -> Mode.PRIMITIVE;
            case null -> throw new IllegalArgumentException("Null generation mode in " + origin);
            default -> throw new IllegalArgumentException("Unknown generation mode '" + value + "' in " + origin
                                                          + ", expected one of " + GeneratedOption.NONE + ", "
                                                          + GeneratedOption.OBJECT + ", " + GeneratedOption.PRIMITIVE);
        };
    }
}
