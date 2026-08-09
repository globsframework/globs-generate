package org.globsframework.model.generator.object;

import org.globsframework.model.generator.AbstractGeneratorGlobFactoryService;
import org.globsframework.model.generator.GenerationOption;

/**
 * Set globs.builder to org.globsframework.model.generator.object.GeneratorGlobFactoryService to make the
 * object flavour the default. Per-type overrides go through the GeneratedOption annotation.
 */
public class GeneratorGlobFactoryService extends AbstractGeneratorGlobFactoryService {

    public GeneratorGlobFactoryService() {
        super(GenerationOption.Mode.OBJECT);
    }
}
