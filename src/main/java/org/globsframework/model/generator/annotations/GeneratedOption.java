package org.globsframework.model.generator.annotations;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.InitUniqueKey;
import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.KeyBuilder;
import org.globsframework.core.model.MutableGlob;

/**
 * What globs-generate should do with the type carrying it : nothing at all, or a generated Glob of one of
 * the two flavours, with or without generated accessors.
 * <p>
 * Both fields are optional, and *unset* is not the same as a value : a field left unset falls back to the
 * system property default, so a type can pin the flavour and leave the accessors to the deployment, or the
 * other way round. See GenerationOption for the resolution.
 * <p>
 * Put it on the type at build time :
 * <pre>
 *   GlobTypeBuilder builder = GlobTypeBuilderFactory.create("MyType");
 *   builder.addAnnotation(GeneratedOption.primitive());
 * </pre>
 * The factory is built at the end of DefaultGlobType's constructor, after the annotations are stored, which
 * is what makes this readable at all — it was the other way round before globs 5.11.
 */
public class GeneratedOption {
    public static final String NONE = "none";
    public static final String OBJECT = "object";
    public static final String PRIMITIVE = "primitive";

    // Compile-time String constants, so that naming them does NOT load this class : that is what lets
    // GenerationOption find the annotation from inside DefaultGlobType's constructor. See the comment there.
    public static final String TYPE_NAME = "GeneratedOption";
    public static final String MODE_NAME = "mode";
    public static final String ACCESSORS_NAME = "accessors";

    public static final GlobType TYPE;

    /** {@link #NONE}, {@link #OBJECT} or {@link #PRIMITIVE}; unset means "whatever the default says". */
    public static final StringField MODE;

    /** Generate one accessor class per field and direction; unset means "whatever the default says". */
    public static final BooleanField ACCESSORS;

    @InitUniqueKey
    public static final Key UNIQUE_KEY;

    public static Glob none() {
        return TYPE.instantiate().set(MODE, NONE);
    }

    public static Glob object() {
        return TYPE.instantiate().set(MODE, OBJECT);
    }

    public static Glob primitive() {
        return TYPE.instantiate().set(MODE, PRIMITIVE);
    }

    public static Glob object(boolean accessors) {
        return TYPE.instantiate().set(MODE, OBJECT).set(ACCESSORS, accessors);
    }

    public static Glob primitive(boolean accessors) {
        return TYPE.instantiate().set(MODE, PRIMITIVE).set(ACCESSORS, accessors);
    }

    /** Only pins the accessors, leaving the flavour to the default. */
    public static Glob withAccessors(boolean accessors) {
        return TYPE.instantiate().set(ACCESSORS, accessors);
    }

    public static Glob create(GeneratedOption_ annotation) {
        MutableGlob option = TYPE.instantiate();
        if (!annotation.mode().isEmpty()) {
            option.set(MODE, annotation.mode());
        }
        if (annotation.accessors().length != 0) {
            option.set(ACCESSORS, annotation.accessors()[0]);
        }
        return option;
    }

    static {
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create(TYPE_NAME);
        MODE = typeBuilder.declareStringField(MODE_NAME);
        ACCESSORS = typeBuilder.declareBooleanField(ACCESSORS_NAME);
        TYPE = typeBuilder.build();
        UNIQUE_KEY = KeyBuilder.newEmptyKey(TYPE);
    }
}
