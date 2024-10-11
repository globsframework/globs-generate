package org.globsframework.model.generator.object;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.GlobFactory;
import org.globsframework.model.generator.FieldVisitorToVisitName;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;


public class AsmGlobObjectGenerator {
    public static final Pattern COMPILE = Pattern.compile("[^\\w]");
    public static GlobType TYPE;
    static AtomicInteger ID = new AtomicInteger();

    synchronized public static GlobFactory create(GlobType globType) {
        try {
            int id = ID.incrementAndGet();
            ClassLoader bytesClassloader = new ClassLoader(AsmGlobObjectGenerator.class.getClassLoader()) {
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.replace('.', '/').equalsIgnoreCase(getGeneratedGlobFactoryName(id))) {
                        byte[] b = generateFactory(globType, id);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    } else if (name.replace('.', '/').equalsIgnoreCase(getGeneratedGlobName(id))) {
                        byte[] b = generateGlob(id, globType);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    } else {
                        return super.findClass(name);
                    }
                }
            };
            try {
                TYPE = globType;
                return (GlobFactory) bytesClassloader.loadClass(getGeneratedGlobFactoryName(id))
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

    private static String getFieldName(Field field) {
        return COMPILE.matcher(field.getName()).replaceAll("_");
    }

    public static byte[] generateGlob(int id, GlobType globType) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;

        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, getGeneratedGlobName(id), null,
                "org/globsframework/model/generator/object/AbstractGeneratedGlob" + (globType.getFieldCount() <= 32 ? "32" : "64"), null);


        Field[] fields = globType.getFields();

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();
        {
            for (Field field : fields) {
                fieldVisitor = classWriter.visitField(ACC_PRIVATE, getFieldName(field), field.safeAccept(visitor.withOutputType()).name, null, null);
                fieldVisitor.visitEnd();
            }
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generator/object/AbstractGeneratedGlob" + (globType.getFieldCount() <= 32 ? "32" : "64"), "<init>", "()V", false);

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "accept", "(Lorg/globsframework/core/metamodel/fields/FieldValueVisitor;)Lorg/globsframework/core/metamodel/fields/FieldValueVisitor;", "<T::Lorg/globsframework/core/metamodel/fields/FieldValueVisitor;>(TT;)TT;", new String[] { "java/lang/Exception" });
            methodVisitor.visitCode();

            for (int i = 0; i < fields.length; i++) {
                Label label3 = new Label();
                Field field = fields[i];
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitIntInsn(BIPUSH, field.getIndex());
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(id), "isSetAt", "(I)Z", false);
                methodVisitor.visitJumpInsn(IFEQ, label3);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(id), getFieldName(field),
                        "Lorg/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name +";");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(id), getFieldName(field), field.safeAccept(visitor.withOutputType()).name);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/core/metamodel/fields/FieldValueVisitor",
                        field.safeAccept(visitor.withMethodVisitor()).name, "(Lorg/globsframework/core/metamodel/fields/"
                                + field.safeAccept(visitor.withFieldType()).name + ";"
                                + field.safeAccept(visitor.withOutputType()).name + ")V",
                        true);
                methodVisitor.visitLabel(label3);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            Label label5 = new Label();
            methodVisitor.visitLabel(label5);
            methodVisitor.visitMaxs(3, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "apply", "(Lorg/globsframework/core/model/FieldValues$Functor;)Lorg/globsframework/core/model/FieldValues$Functor;", "<T::Lorg/globsframework/core/model/FieldValues$Functor;>(TT;)TT;", new String[] { "java/lang/Exception" });
            methodVisitor.visitCode();

            for (int i = 0; i < fields.length; i++) {
                Label label3 = new Label();
                Field field = fields[i];
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitIntInsn(BIPUSH, field.getIndex());
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(id), "isSetAt", "(I)Z", false);
                methodVisitor.visitJumpInsn(IFEQ, label3);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(id), getFieldName(field),
                        "Lorg/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name +";");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(id), getFieldName(field), field.safeAccept(visitor.withOutputType()).name);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/core/model/FieldValues$Functor",
                        "process", "(Lorg/globsframework/core/metamodel/fields/Field;Ljava/lang/Object;)V", true);
                methodVisitor.visitLabel(label3);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            Label label5 = new Label();
            methodVisitor.visitLabel(label5);
            methodVisitor.visitMaxs(3, 2);
            methodVisitor.visitEnd();
        }


        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "doSet",
                    "(Lorg/globsframework/core/metamodel/fields/Field;Ljava/lang/Object;)Lorg/globsframework/core/model/MutableGlob;", null, null);
            methodVisitor.visitCode();
            Label labelReturn = new Label();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/core/metamodel/fields/Field", "getIndex", "()I", true);
                methodVisitor.visitVarInsn(ISTORE, 3);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 3);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(id), "setSetAt", "(I)V", false);
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
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(id),
                    "throwError", "(Lorg/globsframework/core/metamodel/fields/Field;)V", false);

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
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getType", "()Lorg/globsframework/core/metamodel/GlobType;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(id), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "doGet", "(Lorg/globsframework/core/metamodel/fields/Field;)Ljava/lang/Object;", null, null);
            methodVisitor.visitCode();
            Label returnLabel = new Label();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/core/metamodel/fields/Field", "getIndex", "()I", true);

                methodVisitor.visitVarInsn(ISTORE, 2);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(id), "isSetAt", "(I)Z", false);
                Label label1 = new Label();
                methodVisitor.visitJumpInsn(IFNE, label1);
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
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(id), "throwError", "(Lorg/globsframework/core/metamodel/fields/Field;)V", false);
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

    private static String getGeneratedGlobName(int id) {
        return "org/globsframework/model/generated/object/GeneratedGlob_" + id;
    }

    private static String getGeneratedGlobFactoryName(int id) {
        return "org/globsframework/model/generated/object/GeneratedGlobFactory_" + id;
    }


    public static byte[] generateFactory(GlobType globType, int id) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();

        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, getGeneratedGlobFactoryName(id),
                null, "org/globsframework/core/metamodel/impl/DefaultGlobFactory", null);

        {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "TYPE",
                    "Lorg/globsframework/core/metamodel/GlobType;", null, null);
            fieldVisitor.visitEnd();
        }
        {
            for (Field field : globType.getFields()) {
                fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
                        getFieldName(field),
                        "Lorg/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name + ";", null, null);
                fieldVisitor.visitEnd();
            }
        }

        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(id), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/core/metamodel/impl/DefaultGlobFactory", "<init>", "(Lorg/globsframework/core/metamodel/GlobType;)V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "create", "()Lorg/globsframework/core/model/MutableGlob;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitTypeInsn(NEW, getGeneratedGlobName(id));
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, getGeneratedGlobName(id), "<init>", "()V", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generator/object/AsmGlobObjectGenerator", "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
            methodVisitor.visitFieldInsn(PUTSTATIC, getGeneratedGlobFactoryName(id), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");

            for (Field field : globType.getFields()) {
                methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(id),
                        "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
                methodVisitor.visitLdcInsn(field.getName());
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/core/metamodel/GlobType", "findField", "(Ljava/lang/String;)Lorg/globsframework/core/metamodel/fields/Field;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name);
                methodVisitor.visitFieldInsn(PUTSTATIC, getGeneratedGlobFactoryName(id), getFieldName(field),
                        "Lorg/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name + ";");
            }

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(globType.getFieldCount() + 1, 0);
            methodVisitor.visitEnd();
        }

        return classWriter.toByteArray();
    }


    private static class SetFieldVisitor extends org.globsframework.core.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;
        private final FieldVisitorToVisitName visitor;

        public SetFieldVisitor(MethodVisitor methodVisitor, int id, FieldVisitorToVisitName visitor) {
            this.methodVisitor = methodVisitor;
            this.id = id;
            this.visitor = visitor;
        }

        public void notManaged(Field field) {
            methodVisitor.visitTypeInsn(CHECKCAST, field.safeAccept(visitor.withSimpleUserType()).name);
            methodVisitor.visitFieldInsn(PUTFIELD, getGeneratedGlobName(id), getFieldName(field), field.safeAccept(visitor.withOutputType()).name);
        }
    }

    private static class GenerateGetVisitor extends org.globsframework.core.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;

        public GenerateGetVisitor(MethodVisitor methodVisitor, int id) {
            this.methodVisitor = methodVisitor;
            this.id = id;
        }

        public void notManaged(Field field) {
            methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(id),
                    getFieldName(field), field.safeAccept(new FieldVisitorToVisitName().withOutputType()).name);
        }
    }
}
