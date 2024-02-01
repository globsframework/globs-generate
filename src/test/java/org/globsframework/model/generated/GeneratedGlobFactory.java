package org.globsframework.model.generated;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.impl.DefaultGlobFactory;
import org.globsframework.model.MutableGlob;
import org.globsframework.model.generator.AsmGlobGenerator;

public class GeneratedGlobFactory extends DefaultGlobFactory {
    public static final GlobType TYPE;

    static {
        TYPE = AsmGlobGenerator.TYPE;
    }

    public GeneratedGlobFactory() {
        super(TYPE);
    }

    public MutableGlob create() {
        return new GeneratedGlob();
    }
}
