package org.globsframework.model.generated;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.AutoIncrement;
import org.globsframework.core.metamodel.annotations.DefaultBoolean;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.GlobFactoryService;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.get.GlobGetDoubleAccessor;
import org.globsframework.core.model.globaccessor.get.GlobGetIntAccessor;
import org.globsframework.core.model.globaccessor.get.GlobGetLongAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetDoubleAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetLongAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAsmGeneratorTest {
    private String property;

    @BeforeEach
    public void setUp() throws Exception {
        property = System.getProperty("org.globsframework.builder");
        System.setProperty("org.globsframework.builder", getFactoryService());
        GlobFactoryService.Builder.reset();
    }

    public abstract String getFactoryService();

    @AfterEach
    public void tearDown() throws Exception {
        if (property != null) {
            System.setProperty("org.globsframework.builder", property);
        } else {
            System.clearProperty("org.globsframework.builder");
        }
        GlobFactoryService.Builder.reset();
    }

    @Test
    public void checkGetSet() {
        GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("GlobType1");

        IntegerField i1 = globTypeBuilder.declareIntegerField("int");
        DoubleField d1 = globTypeBuilder.declareDoubleField("my double");
        LongField l1 = globTypeBuilder.declareLongField("my long");
        LongArrayField la1 = globTypeBuilder.declareLongArrayField("an array of Long");
        GlobType globType = globTypeBuilder.build();

        MutableGlob instantiate = globType.instantiate();

        Assertions.assertFalse(instantiate.isSet(i1));
        Assertions.assertFalse(instantiate.isSet(d1));
        Assertions.assertFalse(instantiate.isSet(l1));
        Assertions.assertFalse(instantiate.isSet(la1));

        Assertions.assertTrue(instantiate.isNull(i1));
        Assertions.assertTrue(instantiate.isNull(d1));
        Assertions.assertTrue(instantiate.isNull(l1));
        Assertions.assertTrue(instantiate.isNull(la1));

        Assertions.assertNull(instantiate.get(i1));
        Assertions.assertNull(instantiate.get(d1));
        Assertions.assertNull(instantiate.get(l1));
        Assertions.assertNull(instantiate.get(la1));

        instantiate.set(i1, 2);
        Assertions.assertNotNull(instantiate.get(i1));
        Assertions.assertTrue(instantiate.isSet(i1));
        Assertions.assertFalse(instantiate.isSet(d1));
        Assertions.assertFalse(instantiate.isSet(l1));
        Assertions.assertFalse(instantiate.isSet(la1));

        instantiate.set(d1, 2.2);
        Assertions.assertTrue(instantiate.isSet(d1));
        Assertions.assertFalse(instantiate.isSet(l1));
        Assertions.assertFalse(instantiate.isSet(la1));
        instantiate.set(l1, 123);

        Assertions.assertTrue(instantiate.isSet(l1));
        Assertions.assertFalse(instantiate.isSet(la1));

        instantiate.set(la1, new long[]{2, 3});
        Assertions.assertTrue(instantiate.isSet(la1));
        Assertions.assertTrue(instantiate.isSet(l1));
        Assertions.assertTrue(instantiate.isSet(i1));


        Assertions.assertEquals(2, instantiate.get(i1).intValue());
        Assertions.assertEquals(2.2, instantiate.get(d1), 0.01);
        Assertions.assertEquals(123, instantiate.get(l1).longValue());
        Assertions.assertEquals(2, instantiate.get(la1)[0]);
        Assertions.assertEquals(3, instantiate.get(la1)[1]);

        GlobGetIntAccessor iGet = globType.getGlobFactory().getGetAccessor(i1);
        GlobGetDoubleAccessor dGet = globType.getGlobFactory().getGetAccessor(d1);
        GlobGetLongAccessor lGet = globType.getGlobFactory().getGetAccessor(l1);

        GlobSetIntAccessor iSet = globType.getGlobFactory().getSetAccessor(i1);
        GlobSetDoubleAccessor dSet = globType.getGlobFactory().getSetAccessor(d1);
        GlobSetLongAccessor lSet = globType.getGlobFactory().getSetAccessor(l1);

        iSet.set(instantiate, 3);
        dSet.set(instantiate, 3.3);
        lSet.set(instantiate, 321L);

        Assertions.assertEquals(3, iGet.getNative(instantiate));
        Assertions.assertEquals(3.3, dGet.getNative(instantiate), 0.001);
        Assertions.assertEquals(321L, lGet.getNative(instantiate));


        Assertions.assertEquals(3, iGet.get(instantiate).intValue());
        Assertions.assertEquals(3.3, dGet.get(instantiate), 0.001);
        Assertions.assertEquals(321L, lGet.get(instantiate).longValue());

        instantiate.safeApply((field, value) -> System.out.println(field.getName() + ":" + value));
        instantiate.safeAccept(new FieldValueVisitor.AbstractFieldValueVisitor() {
            public void notManaged(Field field, Object value) throws Exception {
                System.out.println(field.getName() + " : " + value);
            }
        });

        iSet.set(instantiate, null);
        Assertions.assertNotNull(instantiate.get(d1));
        Assertions.assertNotNull(instantiate.get(l1));
        dSet.set(instantiate, null);
        Assertions.assertNotNull(instantiate.get(l1));
        lSet.set(instantiate, null);

        Assertions.assertNull(instantiate.get(i1));
        Assertions.assertNull(instantiate.get(d1));
        Assertions.assertNull(instantiate.get(l1));

        Assertions.assertTrue(instantiate.isSet(i1));
        Assertions.assertTrue(instantiate.isSet(d1));
        Assertions.assertTrue(instantiate.isSet(l1));
        Assertions.assertTrue(instantiate.isSet(la1));

        MutableGlob duplicate = instantiate.duplicate();

        Assertions.assertTrue(instantiate.matches(duplicate));
        Assertions.assertNotSame(instantiate, duplicate);

        instantiate.unset(d1);
        Assertions.assertFalse(instantiate.isSet(d1));
        instantiate.unset(i1);
        Assertions.assertFalse(instantiate.isSet(i1));
        instantiate.unset(l1);
        Assertions.assertFalse(instantiate.isSet(l1));
        instantiate.unset(la1);
        Assertions.assertFalse(instantiate.isSet(la1));
    }

    @Test
    public void checkAnnotations() {
        DefaultBoolean.TYPE.instantiate().set(DefaultBoolean.VALUE, true);
        final MutableGlob instantiate = AutoIncrement.TYPE.instantiate();
        Assertions.assertEquals(instantiate.getKey(), AutoIncrement.KEY);
        Assertions.assertSame(instantiate.getType(), AutoIncrement.TYPE);
    }

    @Test
    public void checkGlobWithMoreThan32Field() {
        GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("GlobType1");

        List<Field> allField = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            allField.add(globTypeBuilder.declareIntegerField("int" + i));
            allField.add(globTypeBuilder.declareDoubleField("my double" + i));
            allField.add(globTypeBuilder.declareStringField("my String" + i));
        }
        GlobType globType = globTypeBuilder.build();
        MutableGlob instantiate = globType.instantiate();
        for (Field field : allField) {
            Assertions.assertFalse(instantiate.isSet(field));
        }
        for (Field field : allField) {
            Assertions.assertTrue(instantiate.isNull(field));
            if (field.getName().contains("int")) {
                instantiate.setValue(field, 0);
            } else if (field.getName().contains("double")) {
                instantiate.setValue(field, 0.0);
            } else if (field.getName().contains("String")) {
                instantiate.setValue(field, "STR");
            }
            Assertions.assertTrue(instantiate.isSet(field));
            Assertions.assertFalse(instantiate.isNull(field));
        }
        for (Field field : allField) {
            Assertions.assertTrue(instantiate.isSet(field));
            instantiate.unset(field);
            Assertions.assertFalse(instantiate.isSet(field));
        }
        for (Field field : allField) {
            Assertions.assertFalse(instantiate.isSet(field));
            Assertions.assertTrue(instantiate.isNull(field));
            instantiate.setValue(field, null);
            Assertions.assertTrue(instantiate.isNull(field));
            Assertions.assertTrue(instantiate.isSet(field));
        }
    }
}
