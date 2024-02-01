package org.globsframework.model.generator;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.fields.*;
import org.globsframework.model.GlobFactory;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;


public class AsmGlobGenerator {
    public static final Pattern COMPILE = Pattern.compile("[^\\w]");
    public static GlobType TYPE;
    static AtomicInteger ID = new AtomicInteger();

    synchronized public static GlobFactory create(GlobType globType) {
        try {
            int id = ID.incrementAndGet();
            ClassLoader bytesClassloader = new ClassLoader(AsmGlobGenerator.class.getClassLoader()) {
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.replace('.', '/').equalsIgnoreCase("org/globsframework/model/generated/GeneratedGlobFactory_" + id)) {
                        byte[] b = generateFactory(globType, id);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    } else if (name.replace('.', '/').equalsIgnoreCase("org/globsframework/model/generated/GeneratedGlob_" + id)) {
                        byte[] b = generateGlob(id, globType);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    } else {
                        return super.findClass(name);
                    }
                }
            };
            try {
                TYPE = globType;
                return (GlobFactory) bytesClassloader.loadClass("org/globsframework/model/generated/GeneratedGlobFactory_" + id)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Throwable e) {
                throw new RuntimeException("fail ", e);
            }
        } catch (Throwable e) {
            String mes = "Can not generate bytecode for " + globType.describe() + " : " + e.getMessage();
            throw new RuntimeException(mes, e);
        }

    }


    private static int getIndex(int pos) {
        return pos - 32 * (int) (pos / 32);
    }

    private static String getFieldName(Field field) {
        return COMPILE.matcher(field.getName()).replaceAll("_");
    }

    public static byte[] generateGlob(int id, GlobType globType) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;

        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, "org/globsframework/model/generated/GeneratedGlob_" + id, null,
                "org/globsframework/model/generator/AbstractGeneratedGlob" + (globType.getFieldCount() <= 32 ? "32" : "64"), null);


        Field[] fields = globType.getFields();

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();
        {
            for (Field field : fields) {
                fieldVisitor = classWriter.visitField(ACC_PRIVATE, getFieldName(field), field.safeVisit(visitor.withWithNativeType()).name, null, null);
                fieldVisitor.visitEnd();
            }
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generator/AbstractGeneratedGlob" + (globType.getFieldCount() <= 32 ? "32" : "64"), "<init>", "()V", false);

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "doSet",
                    "(Lorg/globsframework/metamodel/Field;Ljava/lang/Object;)Lorg/globsframework/model/MutableGlob;", null, null);
            methodVisitor.visitCode();
            Label labelReturn = new Label();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/Field", "getIndex", "()I", true);
                methodVisitor.visitVarInsn(ISTORE, 3);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 3);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "setSetAt", "(I)V", false);
                methodVisitor.visitVarInsn(ALOAD, 2);

                Label label0 = new Label();
                methodVisitor.visitJumpInsn(IFNONNULL, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitVarInsn(ILOAD, 3);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,  "forceNull", "(Lorg/globsframework/metamodel/Field;I)V", false);
                methodVisitor.visitJumpInsn(GOTO, labelReturn);

                methodVisitor.visitLabel(label0);
                methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{Opcodes.INTEGER}, 0, null);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 3);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "setNotNull", "(I)V", false);
                methodVisitor.visitVarInsn(ILOAD, 3);

                Label[] labels = IntStream.range(0, fields.length).mapToObj(i -> new Label()).toArray(Label[]::new);

                Label defaultLabel = new Label();
                methodVisitor.visitTableSwitchInsn(0, fields.length - 1, defaultLabel, labels);

                SetFieldVisitor setFieldVisitor = new SetFieldVisitor(methodVisitor, id, visitor);
                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    methodVisitor.visitLabel(labels[i]);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitVarInsn(ALOAD, 2);
                    field.safeVisit(setFieldVisitor);
                    methodVisitor.visitJumpInsn(GOTO, labelReturn);
                }

                methodVisitor.visitLabel(defaultLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                    "throwError", "(Lorg/globsframework/metamodel/Field;)V", false);

            if (fields.length != 0) {
                methodVisitor.visitLabel(labelReturn);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInsn(ARETURN);
            if (fields.length == 0) {
                methodVisitor.visitMaxs(2, 3);
            } else {
                methodVisitor.visitMaxs(3, 4);
            }
            methodVisitor.visitEnd();
        }

        {
            methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "forceNull", "(Lorg/globsframework/metamodel/Field;I)V", null, null);
            methodVisitor.visitCode();
            Label returnLabel = new Label();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "setNull", "(I)V", false);
                methodVisitor.visitVarInsn(ILOAD, 2);

                Label[] labels = IntStream.range(0, fields.length).mapToObj(i -> new Label()).toArray(Label[]::new);

                Label defaultLabel = new Label();
                methodVisitor.visitTableSwitchInsn(0, fields.length - 1, defaultLabel, labels);

                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    methodVisitor.visitLabel(labels[i]);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    field.safeVisit(new GenerateSetNullVisitor(methodVisitor, id));
                    methodVisitor.visitJumpInsn(GOTO, returnLabel);
                }
                methodVisitor.visitLabel(defaultLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "throwError", "(Lorg/globsframework/metamodel/Field;)V", false);


            if (fields.length != 0) {
                methodVisitor.visitLabel(returnLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(fields.length == 0 ? 2 : 3, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getType", "()Lorg/globsframework/metamodel/GlobType;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "TYPE", "Lorg/globsframework/metamodel/GlobType;");
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "doGet", "(Lorg/globsframework/metamodel/Field;)Ljava/lang/Object;", null, null);
            methodVisitor.visitCode();
            Label returnLabel = new Label();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/Field", "getIndex", "()I", true);

                methodVisitor.visitVarInsn(ISTORE, 2);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "isNull", "(I)Z", false);
                Label label0 = new Label();
                methodVisitor.visitJumpInsn(IFNE, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "isSetAt", "(I)Z", false);
                Label label1 = new Label();
                methodVisitor.visitJumpInsn(IFNE, label1);
                methodVisitor.visitLabel(label0);
                methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{Opcodes.INTEGER}, 0, null);
                methodVisitor.visitInsn(ACONST_NULL);
                methodVisitor.visitInsn(ARETURN);
                methodVisitor.visitLabel(label1);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitVarInsn(ILOAD, 2);


                Label[] labels = IntStream.range(0, fields.length).mapToObj(i -> new Label()).toArray(Label[]::new);

                Label defaultLabel = new Label();
                methodVisitor.visitTableSwitchInsn(0, fields.length - 1, defaultLabel, labels);

                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    methodVisitor.visitLabel(labels[i]);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    field.safeVisit(new GenerateGetVisitor(methodVisitor, id));
                    methodVisitor.visitJumpInsn(GOTO, returnLabel);
                }

                methodVisitor.visitLabel(defaultLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "throwError", "(Lorg/globsframework/metamodel/Field;)V", false);
            methodVisitor.visitInsn(ACONST_NULL);
            if (fields.length != 0) {
                methodVisitor.visitLabel(returnLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Object"});
            }
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, fields.length == 0 ? 2 : 3);
            methodVisitor.visitEnd();
        }

        classWriter.visitEnd();

        return classWriter.toByteArray();
    }


    public static byte[] generateFactory(GlobType globType, int id) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;

        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, "org/globsframework/model/generated/GeneratedGlobFactory_" + id,
                null, "org/globsframework/metamodel/impl/DefaultGlobFactory", null);

        {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "TYPE",
                    "Lorg/globsframework/metamodel/GlobType;", null, null);
            fieldVisitor.visitEnd();
        }

        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "TYPE", "Lorg/globsframework/metamodel/GlobType;");
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/metamodel/impl/DefaultGlobFactory", "<init>", "(Lorg/globsframework/metamodel/GlobType;)V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "create", "()Lorg/globsframework/model/MutableGlob;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitTypeInsn(NEW, "org/globsframework/model/generated/GeneratedGlob_" + id);
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "<init>", "()V", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generator/AsmGlobGenerator", "TYPE", "Lorg/globsframework/metamodel/GlobType;");
            methodVisitor.visitFieldInsn(PUTSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "TYPE", "Lorg/globsframework/metamodel/GlobType;");
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 0);
            methodVisitor.visitEnd();
        }

        return classWriter.toByteArray();
    }


    private enum SpecificName {
        visitor,
        fieldType,
        outputTypeSimple,
        outputType,
        nativeType,
        getAccessor,
        setAccessor
    }

    private static class FieldVisitorToVisitName implements org.globsframework.metamodel.fields.FieldVisitor {
        public String name;
        public boolean isArray;
        SpecificName characteristic;

        public void setCharacteristic(SpecificName characteristic) {
            this.characteristic = characteristic;
        }

        public FieldVisitorToVisitName withFieldType() {
            setCharacteristic(SpecificName.fieldType);
            return this;
        }

        public FieldVisitorToVisitName withUserType() {
            setCharacteristic(SpecificName.outputType);
            return this;
        }

        public FieldVisitorToVisitName withSimpleUserType() {
            setCharacteristic(SpecificName.outputTypeSimple);
            return this;
        }

        public FieldVisitorToVisitName withMethodVisitor() {
            setCharacteristic(SpecificName.visitor);
            return this;
        }

        public FieldVisitorToVisitName withAbstractGetAccessor() {
            setCharacteristic(SpecificName.getAccessor);
            return this;
        }

        public FieldVisitorToVisitName withAbstractSetAccessor() {
            setCharacteristic(SpecificName.setAccessor);
            return this;
        }

        public FieldVisitorToVisitName withWithNativeType() {
            setCharacteristic(SpecificName.nativeType);
            return this;
        }

        public void visitInteger(IntegerField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitInteger";
                    break;
                case fieldType:
                    name = "IntegerField";
                    break;
                case outputTypeSimple:
                    name = "java/lang/Integer";
                    break;
                case outputType:
                    name = "Ljava/lang/Integer;";
                    break;
                case nativeType:
                    name = "I";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetIntAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetIntAccessor";
                    break;
            }
        }

        public void visitIntegerArray(IntegerArrayField field) {
            isArray = true;
            switch (characteristic) {
                case visitor:
                    name = "visitIntegerArray";
                    break;
                case fieldType:
                    name = "IntegerArrayField";
                    break;
                case outputTypeSimple:
                case nativeType:
                case outputType:
                    name = "[I";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetIntArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetIntArrayAccessor";
                    break;
            }
        }

        public void visitDouble(DoubleField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitDouble";
                    break;
                case fieldType:
                    name = "DoubleField";
                    break;
                case outputTypeSimple:
                    name = "java/lang/Double";
                    break;
                case outputType:
                    name = "Ljava/lang/Double;";
                    break;
                case nativeType:
                    name = "D";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetDoubleAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetDoubleAccessor";
                    break;
            }
        }

        public void visitBoolean(BooleanField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitBoolean";
                    break;
                case fieldType:
                    name = "BooleanField";
                    break;
                case outputType:
                    name = "Ljava/lang/Boolean;";
                    break;
                case nativeType:
                    name = "Z";
                    break;
                case outputTypeSimple:
                    name = "java/lang/Boolean";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetBooleanAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetBooleanAccessor";
                    break;
            }
        }


        public void visitDoubleArray(DoubleArrayField field) {
            isArray = true;
            switch (characteristic) {
                case visitor:
                    name = "visitDoubleArray";
                    break;
                case fieldType:
                    name = "DoubleArrayField";
                    break;
                case outputTypeSimple:
                case outputType:
                case nativeType:
                    name = "[D";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetDoubleArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetDoubleArrayAccessor";
                    break;
            }
        }

        public void visitBigDecimal(BigDecimalField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitBigDecimal";
                    break;
                case fieldType:
                    name = "BigDecimalField";
                    break;
                case outputTypeSimple:
                    name = "java/math/BigDecimal";
                    break;
                case outputType:
                case nativeType:
                    name = "Ljava/math/BigDecimal;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetBigDecimalArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetBigDecimalArrayAccessor";
                    break;
            }
        }

        public void visitBigDecimalArray(BigDecimalArrayField field) {
            isArray = true;
            switch (characteristic) {
                case visitor:
                    name = "visitBigDecimalArray";
                    break;
                case fieldType:
                    name = "BigDecimalArrayField";
                    break;
                case outputTypeSimple:
                case outputType:
                case nativeType:
                    name = "[Ljava/math/BigDecimal;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetBigDecimalArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetBigDecimalArrayAccessor";
                    break;
            }
        }

        public void visitString(StringField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitString";
                    break;
                case fieldType:
                    name = "StringField";
                    break;
                case outputTypeSimple:
                    name = "java/lang/String";
                    break;
                case outputType:
                case nativeType:
                    name = "Ljava/lang/String;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetStringAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetStringAccessor";
                    break;
            }
        }

        public void visitStringArray(StringArrayField field) {
            isArray = true;
            switch (characteristic) {
                case visitor:
                    name = "visitStringArray";
                    break;
                case fieldType:
                    name = "StringArrayField";
                    break;
                case outputTypeSimple:
                case nativeType:
                case outputType:
                    name = "[Ljava/lang/String;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetStringArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetStringArrayAccessor";
                    break;
            }
        }

        public void visitBooleanArray(BooleanArrayField field) {
            isArray = true;
            switch (characteristic) {
                case visitor:
                    name = "visitBooleanArray";
                    break;
                case fieldType:
                    name = "BooleanArrayField";
                    break;
                case outputTypeSimple:
                case nativeType:
                case outputType:
                    name = "[Z";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetBooleanArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetBooleanArrayAccessor";
                    break;
            }
        }

        public void visitLong(LongField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitLong";
                    break;
                case fieldType:
                    name = "LongField";
                    break;
                case outputTypeSimple:
                    name = "java/lang/Long";
                    break;
                case outputType:
                    name = "Ljava/lang/Long;";
                    break;
                case nativeType:
                    name = "J";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetLongAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetLongAccessor";
                    break;
            }
        }

        public void visitLongArray(LongArrayField field) {
            isArray = true;
            switch (characteristic) {
                case visitor:
                    name = "visitLongArray";
                    break;
                case fieldType:
                    name = "LongArrayField";
                    break;
                case outputTypeSimple:
                case outputType:
                case nativeType:
                    name = "[J";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetLongArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetLongArrayAccessor";
                    break;
            }
        }

        public void visitDate(DateField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitDate";
                    break;
                case fieldType:
                    name = "DateField";
                    break;
                case outputTypeSimple:
                    name = "java/time/LocalDate";
                    break;
                case nativeType:
                case outputType:
                    name = "Ljava/time/LocalDate;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetDateAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetDateAccessor";
                    break;
            }
        }

        public void visitDateTime(DateTimeField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitDateTime";
                    break;
                case fieldType:
                    name = "DateTimeField";
                    break;
                case outputTypeSimple:
                    name = "java/time/ZonedDateTime";
                    break;
                case nativeType:
                case outputType:
                    name = "Ljava/time/ZonedDateTime;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetDateTimeAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetDateTimeAccessor";
                    break;
            }
        }

        public void visitBlob(BlobField field) {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitBlob";
                    break;
                case fieldType:
                    name = "BlobField";
                    break;
                case outputTypeSimple:
                case nativeType:
                case outputType:
                    name = "[B";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetBytesAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetBytesAccessor";
                    break;
            }
        }

        public void visitGlob(GlobField field) throws Exception {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitGlob";
                    break;
                case fieldType:
                    name = "GlobField";
                    break;
                case outputTypeSimple:
                    name = "org/globsframework/model/Glob";
                    break;
                case nativeType:
                case outputType:
                    name = "Lorg/globsframework/model/Glob;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetGlobAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetGlobAccessor";
                    break;
            }
        }

        public void visitGlobArray(GlobArrayField field) throws Exception {
            isArray = true;
            switch (characteristic) {
                case visitor:
                    name = "visitGlobArray";
                    break;
                case fieldType:
                    name = "GlobArrayField";
                    break;
                case outputTypeSimple:
                case nativeType:
                case outputType:
                    name = "[Lorg/globsframework/model/Glob;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetGlobArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetGlobArrayAccessor";
                    break;
            }
        }

        public void visitUnionGlob(GlobUnionField field) throws Exception {
            isArray = false;
            switch (characteristic) {
                case visitor:
                    name = "visitUnionGlob";
                    break;
                case fieldType:
                    name = "GlobUnionField";
                    break;
                case outputTypeSimple:
                    name = "org/globsframework/model/Glob";
                    break;
                case nativeType:
                case outputType:
                    name = "Lorg/globsframework/model/Glob;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetGlobUnionAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetGlobUnionAccessor";
                    break;
            }
        }

        public void visitUnionGlobArray(GlobArrayUnionField field) throws Exception {
            isArray = true;
            switch (characteristic) {
                case visitor:
                    name = "visitUnionGlobArray";
                    break;
                case fieldType:
                    name = "GlobArrayUnionField";
                    break;
                case outputTypeSimple:
                case nativeType:
                case outputType:
                    name = "[Lorg/globsframework/model/Glob;";
                    break;
                case getAccessor:
                    name = "AbstractGlobGetGlobUnionArrayAccessor";
                    break;
                case setAccessor:
                    name = "AbstractGlobSetGlobUnionArrayAccessor";
                    break;
            }
        }
    }

    private static class SetFieldVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;
        private final FieldVisitorToVisitName visitor;

        public SetFieldVisitor(MethodVisitor methodVisitor, int id, FieldVisitorToVisitName visitor) {
            this.methodVisitor = methodVisitor;
            this.id = id;
            this.visitor = visitor;
        }

        public void visitInteger(IntegerField field) throws Exception {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "I");
        }

        public void visitLong(LongField field) throws Exception {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "J");
        }

        public void visitDouble(DoubleField field) throws Exception {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "D");
        }

        public void visitBoolean(BooleanField field) throws Exception {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "Z");
        }

        public void notManaged(Field field) throws Exception {
            methodVisitor.visitTypeInsn(CHECKCAST, field.safeVisit(visitor.withSimpleUserType()).name);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), field.safeVisit(visitor.withUserType()).name);
        }
    }

    private static class GenerateGetVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;

        public GenerateGetVisitor(MethodVisitor methodVisitor, int id) {
            this.methodVisitor = methodVisitor;
            this.id = id;
        }

        public void visitInteger(IntegerField field) throws Exception {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "I");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        }

        public void visitDouble(DoubleField field) throws Exception {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "D");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        }

        public void visitLong(LongField field) throws Exception {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "J");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        }

        public void visitBoolean(BooleanField field) throws Exception {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "Z");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        }

        public void notManaged(Field field) throws Exception {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                    getFieldName(field), field.safeVisit(new FieldVisitorToVisitName().withUserType()).name);
        }
    }

    private static class GenerateSetNullVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;

        public GenerateSetNullVisitor(MethodVisitor methodVisitor, int id) {
            this.methodVisitor = methodVisitor;
            this.id = id;
        }

        public void visitInteger(IntegerField field) throws Exception {
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "I");
        }

        public void visitDouble(DoubleField field) throws Exception {
            methodVisitor.visitInsn(DCONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "D");
        }

        public void visitLong(LongField field) throws Exception {
            methodVisitor.visitInsn(LCONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "J");
        }

        public void visitBoolean(BooleanField field) throws Exception {
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "Z");
        }

        public void notManaged(Field field) throws Exception {
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                    getFieldName(field), field.safeVisit(new FieldVisitorToVisitName().withUserType()).name);
        }
    }
}
