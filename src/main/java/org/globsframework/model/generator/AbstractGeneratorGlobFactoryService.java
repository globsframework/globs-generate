package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.impl.DefaultGlobFactory;
import org.globsframework.core.model.GlobFactory;
import org.globsframework.core.model.GlobFactoryService;
import org.globsframework.model.generator.object.AsmGlobObjectGenerator;
import org.globsframework.model.generator.primitive.AsmGlobPrimitiveGenerator;

/**
 * The shared half of the two services selected through {@code globs.builder} : the only thing that differs
 * between them is the flavour a type gets when it asks for nothing. Whichever one is installed, a type
 * annotated with {@link org.globsframework.model.generator.annotations.GeneratedOption} gets what it asks
 * for — including the other flavour, or nothing at all.
 */
public abstract class AbstractGeneratorGlobFactoryService implements GlobFactoryService {
    private final GenerationOption.Mode defaultMode;

    protected AbstractGeneratorGlobFactoryService(GenerationOption.Mode defaultMode) {
        this.defaultMode = defaultMode;
    }

    public GlobFactory getFactory(GlobType type) {
        GenerationOption option = GenerationOption.resolve(type, defaultMode);
        if (option.mode() == GenerationOption.Mode.NONE) {
            // null is how a GlobFactoryService says "not mine" : DispatchGlobFactoryService then falls back
            // to core's DefaultGlobFactoryService, i.e. exactly what an unconfigured JVM does.
            return null;
        }
        if (type.getFieldCount() > 64) {
            // the masks of the generated bases are 64 bits wide at most
            return new DefaultGlobFactory(type);
        }
        return switch (option.mode()) {
            case OBJECT -> AsmGlobObjectGenerator.create(type, option.accessors());
            case PRIMITIVE -> AsmGlobPrimitiveGenerator.create(type, option.accessors());
            case NONE -> null;
        };
    }
}
