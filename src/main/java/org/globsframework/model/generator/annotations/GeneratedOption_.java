package org.globsframework.model.generator.annotations;

import org.globsframework.core.metamodel.GlobType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The Java form of {@link GeneratedOption}. {@code accessors} is an array so that "not specified" can be
 * told apart from {@code false} : an empty array leaves the field unset, and the default applies.
 */
@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(ElementType.TYPE)
public @interface GeneratedOption_ {

    /** "none", "object" or "primitive"; empty leaves it to the default. */
    String mode() default "";

    boolean[] accessors() default {};

    GlobType TYPE = GeneratedOption.TYPE;
}
