package org.globsframework.model.generated.object;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.model.generated.AbstractAsmGeneratorTest;
import org.junit.jupiter.api.Assertions;

public class AsmGlobObjectGeneratorTest extends AbstractAsmGeneratorTest {

    public static class Test1 {
        public static final GlobType TYPE;

        public static final StringField name;

        static {
            GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("Test1");
            name = typeBuilder.declareStringField("name");
            TYPE = typeBuilder.build();
        }
    }

    @org.junit.jupiter.api.Test
    public void initFromType() {
        Assertions.assertNotNull(Test1.TYPE);
    }

    @Override
    public String getFactoryService() {
        return "org.globsframework.model.generator.object.GeneratorGlobFactoryService";
    }
}
