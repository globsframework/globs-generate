package org.globsframework.model.generator;

import org.globsframework.metamodel.GlobType;
import org.globsframework.model.GlobFactory;
import org.globsframework.model.GlobFactoryService;

public class GeneratorGlobFactoryService implements GlobFactoryService {

    public GlobFactory get(GlobType type) {
        return AsmGlobGenerator.create(type);
    }
}
