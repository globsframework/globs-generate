package org.globsframework.model.generated.primitive;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.model.generated.AbstractAsmGeneratorTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AsmGlobPrimitiveGeneratorTest extends AbstractAsmGeneratorTest {

    public String getFactoryService() {
        return "org.globsframework.model.generator.primitive.GeneratorGlobFactoryService";
    }

    public static class Test1 {
        public static GlobType TYPE;

        public static final StringField name;

        static {
            GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("Test1");
            name = typeBuilder.declareStringField("name");
            TYPE = typeBuilder.build();
        }
    }

    @Test
    public void initFromType() {
        Assertions.assertNotNull(Test1.TYPE);
    }
}
