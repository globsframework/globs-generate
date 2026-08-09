package org.globsframework.model.generator.primitive;

import org.globsframework.model.generator.AbstractGeneratorGlobFactoryService;
import org.globsframework.model.generator.GenerationOption;

/**
 * Set globs.builder to org.globsframework.model.generator.primitive.GeneratorGlobFactoryService to make the
 * primitive flavour the default. Per-type overrides go through the GeneratedOption annotation.
 */
public class GeneratorGlobFactoryService extends AbstractGeneratorGlobFactoryService {

    public GeneratorGlobFactoryService() {
        super(GenerationOption.Mode.PRIMITIVE);
    }
}
