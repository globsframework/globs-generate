package org.globsframework.model.generator;

import org.globsframework.metamodel.fields.Field;
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
                fieldVisitor = classWriter.visitField(ACC_PRIVATE, getFieldName(field), field.safeAccept(visitor.withWithNativeType()).name, null, null);
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
                    "(Lorg/globsframework/metamodel/fields/Field;Ljava/lang/Object;)Lorg/globsframework/model/MutableGlob;", null, null);
            methodVisitor.visitCode();
            Label labelReturn = new Label();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/fields/Field", "getIndex", "()I", true);
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
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,  "forceNull", "(Lorg/globsframework/metamodel/fields/Field;I)V", false);
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
                    field.safeAccept(setFieldVisitor);
                    methodVisitor.visitJumpInsn(GOTO, labelReturn);
                }

                methodVisitor.visitLabel(defaultLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                    "throwError", "(Lorg/globsframework/metamodel/fields/Field;)V", false);

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
            methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "forceNull", "(Lorg/globsframework/metamodel/fields/Field;I)V", null, null);
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
                    field.safeAccept(new GenerateSetNullVisitor(methodVisitor, id));
                    methodVisitor.visitJumpInsn(GOTO, returnLabel);
                }
                methodVisitor.visitLabel(defaultLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "throwError", "(Lorg/globsframework/metamodel/fields/Field;)V", false);


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
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "doGet", "(Lorg/globsframework/metamodel/fields/Field;)Ljava/lang/Object;", null, null);
            methodVisitor.visitCode();
            Label returnLabel = new Label();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/fields/Field", "getIndex", "()I", true);

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
                    field.safeAccept(new GenerateGetVisitor(methodVisitor, id));
                    methodVisitor.visitJumpInsn(GOTO, returnLabel);
                }

                methodVisitor.visitLabel(defaultLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "throwError", "(Lorg/globsframework/metamodel/fields/Field;)V", false);
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
            name = switch (characteristic) {
                case visitor -> "visitInteger";
                case fieldType -> "IntegerField";
                case outputTypeSimple -> "java/lang/Integer";
                case outputType -> "Ljava/lang/Integer;";
                case nativeType -> "I";
                case getAccessor -> "AbstractGlobGetIntAccessor";
                case setAccessor -> "AbstractGlobSetIntAccessor";
            };
        }

        public void visitIntegerArray(IntegerArrayField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitIntegerArray";
                case fieldType -> "IntegerArrayField";
                case outputTypeSimple, nativeType, outputType -> "[I";
                case getAccessor -> "AbstractGlobGetIntArrayAccessor";
                case setAccessor -> "AbstractGlobSetIntArrayAccessor";
            };
        }

        public void visitDouble(DoubleField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitDouble";
                case fieldType -> "DoubleField";
                case outputTypeSimple -> "java/lang/Double";
                case outputType -> "Ljava/lang/Double;";
                case nativeType -> "D";
                case getAccessor -> "AbstractGlobGetDoubleAccessor";
                case setAccessor -> "AbstractGlobSetDoubleAccessor";
            };
        }

        public void visitBoolean(BooleanField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitBoolean";
                case fieldType -> "BooleanField";
                case outputType -> "Ljava/lang/Boolean;";
                case nativeType -> "Z";
                case outputTypeSimple -> "java/lang/Boolean";
                case getAccessor -> "AbstractGlobGetBooleanAccessor";
                case setAccessor -> "AbstractGlobSetBooleanAccessor";
            };
        }


        public void visitDoubleArray(DoubleArrayField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitDoubleArray";
                case fieldType -> "DoubleArrayField";
                case outputTypeSimple, outputType, nativeType -> "[D";
                case getAccessor -> "AbstractGlobGetDoubleArrayAccessor";
                case setAccessor -> "AbstractGlobSetDoubleArrayAccessor";
            };
        }

        public void visitBigDecimal(BigDecimalField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitBigDecimal";
                case fieldType -> "BigDecimalField";
                case outputTypeSimple -> "java/math/BigDecimal";
                case outputType, nativeType -> "Ljava/math/BigDecimal;";
                case getAccessor -> "AbstractGlobGetBigDecimalArrayAccessor";
                case setAccessor -> "AbstractGlobSetBigDecimalArrayAccessor";
            };
        }

        public void visitBigDecimalArray(BigDecimalArrayField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitBigDecimalArray";
                case fieldType -> "BigDecimalArrayField";
                case outputTypeSimple, outputType, nativeType -> "[Ljava/math/BigDecimal;";
                case getAccessor -> "AbstractGlobGetBigDecimalArrayAccessor";
                case setAccessor -> "AbstractGlobSetBigDecimalArrayAccessor";
            };
        }

        public void visitString(StringField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitString";
                case fieldType -> "StringField";
                case outputTypeSimple -> "java/lang/String";
                case outputType, nativeType -> "Ljava/lang/String;";
                case getAccessor -> "AbstractGlobGetStringAccessor";
                case setAccessor -> "AbstractGlobSetStringAccessor";
            };
        }

        public void visitStringArray(StringArrayField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitStringArray";
                case fieldType -> "StringArrayField";
                case outputTypeSimple, nativeType, outputType -> "[Ljava/lang/String;";
                case getAccessor -> "AbstractGlobGetStringArrayAccessor";
                case setAccessor -> "AbstractGlobSetStringArrayAccessor";
            };
        }

        public void visitBooleanArray(BooleanArrayField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitBooleanArray";
                case fieldType -> "BooleanArrayField";
                case outputTypeSimple, nativeType, outputType -> "[Z";
                case getAccessor -> "AbstractGlobGetBooleanArrayAccessor";
                case setAccessor -> "AbstractGlobSetBooleanArrayAccessor";
            };
        }

        public void visitLong(LongField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitLong";
                case fieldType -> "LongField";
                case outputTypeSimple -> "java/lang/Long";
                case outputType -> "Ljava/lang/Long;";
                case nativeType -> "J";
                case getAccessor -> "AbstractGlobGetLongAccessor";
                case setAccessor -> "AbstractGlobSetLongAccessor";
            };
        }

        public void visitLongArray(LongArrayField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitLongArray";
                case fieldType -> "LongArrayField";
                case outputTypeSimple, outputType, nativeType -> "[J";
                case getAccessor -> "AbstractGlobGetLongArrayAccessor";
                case setAccessor -> "AbstractGlobSetLongArrayAccessor";
            };
        }

        public void visitDate(DateField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitDate";
                case fieldType -> "DateField";
                case outputTypeSimple -> "java/time/LocalDate";
                case nativeType, outputType -> "Ljava/time/LocalDate;";
                case getAccessor -> "AbstractGlobGetDateAccessor";
                case setAccessor -> "AbstractGlobSetDateAccessor";
            };
        }

        public void visitDateTime(DateTimeField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitDateTime";
                case fieldType -> "DateTimeField";
                case outputTypeSimple -> "java/time/ZonedDateTime";
                case nativeType, outputType -> "Ljava/time/ZonedDateTime;";
                case getAccessor -> "AbstractGlobGetDateTimeAccessor";
                case setAccessor -> "AbstractGlobSetDateTimeAccessor";
            };
        }

        public void visitBlob(BlobField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitBlob";
                case fieldType -> "BlobField";
                case outputTypeSimple, nativeType, outputType -> "[B";
                case getAccessor -> "AbstractGlobGetBytesAccessor";
                case setAccessor -> "AbstractGlobSetBytesAccessor";
            };
        }

        public void visitGlob(GlobField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitGlob";
                case fieldType -> "GlobField";
                case outputTypeSimple -> "org/globsframework/model/Glob";
                case nativeType, outputType -> "Lorg/globsframework/model/Glob;";
                case getAccessor -> "AbstractGlobGetGlobAccessor";
                case setAccessor -> "AbstractGlobSetGlobAccessor";
            };
        }

        public void visitGlobArray(GlobArrayField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitGlobArray";
                case fieldType -> "GlobArrayField";
                case outputTypeSimple, nativeType, outputType -> "[Lorg/globsframework/model/Glob;";
                case getAccessor -> "AbstractGlobGetGlobArrayAccessor";
                case setAccessor -> "AbstractGlobSetGlobArrayAccessor";
            };
        }

        public void visitUnionGlob(GlobUnionField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitUnionGlob";
                case fieldType -> "GlobUnionField";
                case outputTypeSimple -> "org/globsframework/model/Glob";
                case nativeType, outputType -> "Lorg/globsframework/model/Glob;";
                case getAccessor -> "AbstractGlobGetGlobUnionAccessor";
                case setAccessor -> "AbstractGlobSetGlobUnionAccessor";
            };
        }

        public void visitUnionGlobArray(GlobArrayUnionField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitUnionGlobArray";
                case fieldType -> "GlobArrayUnionField";
                case outputTypeSimple, nativeType, outputType -> "[Lorg/globsframework/model/Glob;";
                case getAccessor -> "AbstractGlobGetGlobUnionArrayAccessor";
                case setAccessor -> "AbstractGlobSetGlobUnionArrayAccessor";
            };
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

        public void visitInteger(IntegerField field) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "I");
        }

        public void visitLong(LongField field) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "J");
        }

        public void visitDouble(DoubleField field) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "D");
        }

        public void visitBoolean(BooleanField field) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "Z");
        }

        public void notManaged(Field field) {
            methodVisitor.visitTypeInsn(CHECKCAST, field.safeAccept(visitor.withSimpleUserType()).name);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), field.safeAccept(visitor.withUserType()).name);
        }
    }

    private static class GenerateGetVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;

        public GenerateGetVisitor(MethodVisitor methodVisitor, int id) {
            this.methodVisitor = methodVisitor;
            this.id = id;
        }

        public void visitInteger(IntegerField field) {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "I");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        }

        public void visitDouble(DoubleField field) {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "D");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        }

        public void visitLong(LongField field) {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "J");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        }

        public void visitBoolean(BooleanField field) {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "Z");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        }

        public void notManaged(Field field) {
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                    getFieldName(field), field.safeAccept(new FieldVisitorToVisitName().withUserType()).name);
        }
    }

    private static class GenerateSetNullVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;

        public GenerateSetNullVisitor(MethodVisitor methodVisitor, int id) {
            this.methodVisitor = methodVisitor;
            this.id = id;
        }

        public void visitInteger(IntegerField field) {
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "I");
        }

        public void visitDouble(DoubleField field) {
            methodVisitor.visitInsn(DCONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "D");
        }

        public void visitLong(LongField field) {
            methodVisitor.visitInsn(LCONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "J");
        }

        public void visitBoolean(BooleanField field) {
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getFieldName(field), "Z");
        }

        public void notManaged(Field field) {
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                    getFieldName(field), field.safeAccept(new FieldVisitorToVisitName().withUserType()).name);
        }
    }
}
