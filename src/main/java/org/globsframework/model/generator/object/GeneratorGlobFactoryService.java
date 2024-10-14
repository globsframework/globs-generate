package org.globsframework.model.generator.object;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.impl.DefaultGlobFactory;
import org.globsframework.core.model.GlobFactory;
import org.globsframework.core.model.GlobFactoryService;

// set propertty org.globsframework.builder to org.globsframework.model.generator.object.GeneratorGlobFactoryService
// to activate

public class GeneratorGlobFactoryService implements GlobFactoryService {

    public GlobFactory getFactory(GlobType type) {
        if (type.getFieldCount() <= 64) {
            return AsmGlobObjectGenerator.create(type);
        } else {
            return new DefaultGlobFactory(type);
        }
    }
}
