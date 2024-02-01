package org.globsframework.model.generator;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.impl.DefaultGlobFactory;
import org.globsframework.model.GlobFactory;
import org.globsframework.model.GlobFactoryService;

public class GeneratorGlobFactoryService implements GlobFactoryService {

    public GlobFactory getFactory(GlobType type) {
        if (type.getFieldCount() <= 64) {
            return AsmGlobGenerator.create(type);
        }
        else {
            return new DefaultGlobFactory(type);
        }
    }
}
