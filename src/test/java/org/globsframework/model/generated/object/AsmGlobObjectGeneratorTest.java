package org.globsframework.model.generated.object;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.model.generated.AbstractAsmGeneratorTest;
import org.junit.Assert;
import org.junit.Test;

public class AsmGlobObjectGeneratorTest extends AbstractAsmGeneratorTest {

    public static class Test1 {
        public static GlobType TYPE;

        public static StringField name;

        static {
            GlobTypeLoaderFactory.create(Test1.class).load();
        }
    }

    @Test
    public void initFromType() {
        Assert.assertNotNull(Test1.TYPE);
    }

    @Override
    public String getFactoryService() {
        return "org.globsframework.model.generator.object.GeneratorGlobFactoryService";
    }
}
