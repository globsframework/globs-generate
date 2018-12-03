package org.globsframework.model.generator;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.fields.*;
import org.globsframework.model.GlobFactory;
import org.objectweb.asm.*;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;


// a simple GlobGenerator where all type are object, native type are not managed.

public class AsmGlobGenerator {
    public static final Pattern COMPILE = Pattern.compile("[^\\w]");
    public static final String SET_ACCESSOR = "SetAccessor";
    public static final String GET_ACCESSOR = "GetAccessor";
    public static final String NULL_FLAGS = "nullFlags_";
    static AtomicInteger ID = new AtomicInteger();
    public static GlobType TYPE;


    public static GlobFactory create(GlobType globType) {
        try {
            int id = ID.incrementAndGet();
            TYPE = globType;
            ClassLoader bytesClassloader = new ClassLoader(AsmGlobGenerator.class.getClassLoader()) {
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.replace('.', '/').equalsIgnoreCase("org/globsframework/model/generated/GeneratedGlobFactory_" + id)) {
                        byte[] b = generateFactory(globType, id);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    } else if (name.replace('.', '/').equalsIgnoreCase("org/globsframework/model/generated/GeneratedGlob_" + id)) {
                        byte[] b = generateGlob(id, globType);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    } else if (name.replace('.', '/').startsWith("org/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR)) {
                        String fieldName = name.replace('.', '/').replace("org/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR, "");
                        for (Field field : globType.getFields()) {
                            if (fieldName.equals(getName(field))) {
                                byte[] b = generateGetInner(id, field);
                                return super.defineClass(name.replace("/", "."), b, 0, b.length);
                            }
                        }
                        throw new RuntimeException("Can not find field " + fieldName + " got " + globType.describe());
                    } else if (name.replace('.', '/').startsWith("org/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR)) {
                        String fieldName = name.replace('.', '/').replace("org/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR, "");
                        for (Field field : globType.getFields()) {
                            if (fieldName.equals(getName(field))) {
                                byte[] b = generateSetInner(id, field);
                                return super.defineClass(name.replace("/", "."), b, 0, b.length);
                            }
                        }
                        throw new RuntimeException("Can not find field " + fieldName + " got " + globType.describe());
                    } else {
                        return super.findClass(name);
                    }
                }
            };
            try {
                return (GlobFactory) bytesClassloader.loadClass("org/globsframework/model/generated/GeneratedGlobFactory_" + id).newInstance();
            } catch (Throwable e) {
                throw new RuntimeException("fail ", e);
            }
        } catch (Throwable e) {
            String mes = "Can not generate bytecode for " + globType.describe() + " : " + e.getMessage();
            throw new RuntimeException(mes, e);
        }

    }

    private static String getNullFieldName(int pos) {
        return NULL_FLAGS + (pos / 32);
    }

    private static int getNullFieldIndex(int pos) {
        return (pos / 32);
    }

    private static int getIndex(int pos) {
        return pos - 32 * (int) (pos / 32);
    }

    private static String getName(Field field) {
        return COMPILE.matcher(field.getName()).replaceAll("_");
    }

    public static byte[] generateGetInner(int id, Field field) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();


        classWriter.visit(V1_8, ACC_SUPER, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field), null,
                "org/globsframework/model/globaccessor/get/impl/" + field.safeVisit(visitor.withAbstractGetAccessor()).name, null);

        classWriter.visitSource("GeneratedGlob.java", null);

        classWriter.visitInnerClass("org/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field),
                "org/globsframework/model/generated/GeneratedGlob_" + id, GET_ACCESSOR + getName(field), ACC_PRIVATE | ACC_STATIC);


        {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "INSTANCE",
                    "Lorg/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field) + ";", null, null);
            fieldVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/globaccessor/get/impl/" + field.safeVisit(visitor.withAbstractGetAccessor()).name, "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "get",
                    "(Lorg/globsframework/model/Glob;)" + field.safeVisit(visitor.withUserType()).name, null, null);
            methodVisitor.visitCode();

            MethodVisitor finalMethodVisitor = methodVisitor;
            field.safeVisit(new org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor() {
                public void visitInteger(IntegerField field) throws Exception {
                    int nullPos = getNullPos(field);
                    finalMethodVisitor.visitVarInsn(ALOAD, 1);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/model/generated/GeneratedGlob_" + id);
                    finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            "get_" + getName(field), "()Ljava/lang/Integer;", false);
                }

                public void visitDouble(DoubleField field) throws Exception {
                    int nullPos = getNullPos(field);
                    finalMethodVisitor.visitVarInsn(ALOAD, 1);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/model/generated/GeneratedGlob_" + id);
                    finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            "get_" + getName(field), "()Ljava/lang/Double;", false);
                }

                public void visitLong(LongField field) throws Exception {
                    int nullPos = getNullPos(field);
                    finalMethodVisitor.visitVarInsn(ALOAD, 1);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/model/generated/GeneratedGlob_" + id);
                    finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            "get_" + getName(field), "()Ljava/lang/Long;", false);
                }

                public void notManaged(Field field) throws Exception {
                    finalMethodVisitor.visitVarInsn(ALOAD, 1);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/model/generated/GeneratedGlob_" + id);
                    finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getName(field), field.safeVisit(visitor.withUserType()).name);
                }
            });
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(102, label0);
            methodVisitor.visitTypeInsn(NEW, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field));
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field), "<init>", "()V", false);
            methodVisitor.visitFieldInsn(PUTSTATIC, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field), "INSTANCE",
                    "Lorg/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field) + ";");
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 0);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    public static byte[] generateSetInner(int id, Field field) {

        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;
        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();

        classWriter.visit(V1_8, ACC_FINAL | ACC_SUPER, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field), null,
                "org/globsframework/model/globaccessor/set/impl/" + field.safeVisit(visitor.withAbstractSetAccessor()).name, null);


        classWriter.visitInnerClass("org/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field), "org/globsframework/model/generated/GeneratedGlob_" + id,
                SET_ACCESSOR + getName(field), ACC_PRIVATE | ACC_FINAL | ACC_STATIC);

        {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "INSTANCE",
                    "Lorg/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field) + ";", null, null);
            fieldVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/globaccessor/set/impl/" + field.safeVisit(visitor.withAbstractSetAccessor()).name, "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "set", "(Lorg/globsframework/model/MutableGlob;" + field.safeVisit(visitor.withUserType()).name + ")V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 2);

            MethodVisitor finalMethodVisitor = methodVisitor;
            field.safeVisit(new org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor() {
                public void visitInteger(IntegerField field) throws Exception {
                    finalMethodVisitor.visitVarInsn(ALOAD, 1);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/model/generated/GeneratedGlob_" + id);
                    finalMethodVisitor.visitVarInsn(ALOAD, 2);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                    finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            "set_" + getName(field), "(Ljava/lang/Integer;)V", false);
                }

                public void visitDouble(DoubleField field) throws Exception {
                    finalMethodVisitor.visitVarInsn(ALOAD, 1);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/model/generated/GeneratedGlob_" + id);
                    finalMethodVisitor.visitVarInsn(ALOAD, 2);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
                    finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            "set_" + getName(field), "(Ljava/lang/Double;)V", false);
                }

                public void visitLong(LongField field) throws Exception {
                    finalMethodVisitor.visitVarInsn(ALOAD, 1);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/model/generated/GeneratedGlob_" + id);
                    finalMethodVisitor.visitVarInsn(ALOAD, 2);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
                    finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            "set_" + getName(field), "(Ljava/lang/Long;)V", false);
                }

                public void notManaged(Field field) throws Exception {
                    finalMethodVisitor.visitVarInsn(ALOAD, 1);
                    finalMethodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/model/generated/GeneratedGlob_" + id);
                    finalMethodVisitor.visitVarInsn(ALOAD, 2);
                    finalMethodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), field.safeVisit(visitor.withUserType()).name);
                    finalMethodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                }
            });

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(3, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitTypeInsn(NEW, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field));
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field), "<init>", "()V", false);
            methodVisitor.visitFieldInsn(PUTSTATIC, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field), "INSTANCE",
                    "Lorg/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field) + ";");
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 0);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    private static int getNullPos(Field field) {
        int nullPos = 0;
        for (Field field1 : field.getGlobType().getFields()) {
            if (field1 == field) {
                break;
            }
            if (field instanceof DoubleField || field instanceof IntegerField) {
                nullPos++;
            }
        }
        return nullPos;
    }

    public static byte[] generateGlob(int id, GlobType globType) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;
        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();

        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "org/globsframework/model/generated/GeneratedGlob_" + id, null, "org/globsframework/model/generator/AbstractGeneratedGlob", null);


        Field[] fields = globType.getFields();
        for (Field field : fields) {
            classWriter.visitInnerClass("org/globsframework/model/generated/GeneratedGlob_" + "$" + GET_ACCESSOR + getName(field), "org/globsframework/model/generated/GeneratedGlob_" + id,
                    GET_ACCESSOR + getName(field), ACC_PRIVATE | ACC_STATIC);
            classWriter.visitInnerClass("org/globsframework/model/generated/GeneratedGlob_" + "$" + SET_ACCESSOR + getName(field), "org/globsframework/model/generated/GeneratedGlob_" + id,
                    SET_ACCESSOR + getName(field), ACC_PRIVATE | ACC_STATIC);
        }


        classWriter.visitInnerClass("org/globsframework/model/FieldValues$Functor", "org/globsframework/model/FieldValues", "Functor", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

        {
            int longNullCount = (globType.saveAccept(new CountNeedNullFieldVisitor()).count / 32) + 1;
            for (int i = 0; i < longNullCount; i++) {
                classWriter.visitField(ACC_PROTECTED, NULL_FLAGS + i, "I", null, null);
            }
        }

        {
            for (Field field : fields) {
                fieldVisitor = classWriter.visitField(ACC_PROTECTED, getName(field), field.safeVisit(visitor.withWithNativeType()).name, null, null);
                fieldVisitor.visitEnd();
            }
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generator/AbstractGeneratedGlob", "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "doSet",
                    "(Lorg/globsframework/metamodel/Field;Ljava/lang/Object;)Lorg/globsframework/model/MutableGlob;", null, null);
            methodVisitor.visitCode();
            if (fields.length != 0) {

                methodVisitor.visitVarInsn(ALOAD, 2);
                Label label1 = new Label();
                methodVisitor.visitJumpInsn(IFNONNULL, label1);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                        "setNull", "(Lorg/globsframework/metamodel/Field;)V", false);
                Label labelReturn = new Label();
                methodVisitor.visitJumpInsn(GOTO, labelReturn);
                methodVisitor.visitLabel(label1);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/Field", "getIndex", "()I", true);

                Label[] labels = IntStream.range(0, fields.length).mapToObj(i -> new Label()).toArray(Label[]::new);

                Label label = new Label();
                methodVisitor.visitTableSwitchInsn(0, fields.length - 1, label, labels);

                SetFieldVisitor setFieldVisitor = new SetFieldVisitor(methodVisitor, id, visitor);
                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    methodVisitor.visitLabel(labels[i]);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    field.safeVisit(setFieldVisitor);
                    methodVisitor.visitJumpInsn(GOTO, labelReturn);
                }

                methodVisitor.visitLabel(label);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                        "throwError", "(Lorg/globsframework/metamodel/Field;)V", false);

                methodVisitor.visitLabel(labelReturn);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(3, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getType", "()Lorg/globsframework/metamodel/GlobType;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "GLOB_TYPE", "Lorg/globsframework/metamodel/GlobType;");
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "doGet", "(Lorg/globsframework/metamodel/Field;)Ljava/lang/Object;", null, null);
            methodVisitor.visitCode();
            if (fields.length != 0) {

                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/Field", "getIndex", "()I", true);

                Label[] labels = IntStream.range(0, fields.length).mapToObj(i -> new Label()).toArray(Label[]::new);

                Label label = new Label();
                methodVisitor.visitTableSwitchInsn(0, fields.length - 1, label, labels);

                GetWithNullFieldVisitor getWithNullFieldVisitor = new GetWithNullFieldVisitor(methodVisitor, id, visitor);
                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    methodVisitor.visitLabel(labels[i]);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    field.safeVisit(getWithNullFieldVisitor);
                }
                methodVisitor.visitLabel(label);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id, "throwError", "(Lorg/globsframework/metamodel/Field;)V", false);
            }
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            generateSetNull("org/globsframework/model/generated/GeneratedGlob_" + id, globType, classWriter);
        }
        {
            for (Field field : fields) {
                methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "get_" + getName((field)),
                        "()" + field.safeVisit(new FieldVisitorToVisitName().withUserType()).name, null, null);
                MethodVisitor finalMethodVisitor = methodVisitor;
                field.safeVisit(new org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor() {
                    int nullPos = 0;

                    public void visitInteger(IntegerField field) throws Exception {
                        finalMethodVisitor.visitCode();
                        finalMethodVisitor.visitVarInsn(ALOAD, 0);
                        finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                                getNullFieldName(nullPos), "I");
                        finalMethodVisitor.visitLdcInsn(1 << getIndex(nullPos));
                        finalMethodVisitor.visitInsn(IAND);
                        Label label1 = new Label();
                        finalMethodVisitor.visitJumpInsn(IFNE, label1);
                        finalMethodVisitor.visitVarInsn(ALOAD, 0);
                        finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "I");
                        finalMethodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        Label label2 = new Label();
                        finalMethodVisitor.visitJumpInsn(GOTO, label2);
                        finalMethodVisitor.visitLabel(label1);
                        finalMethodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                        finalMethodVisitor.visitInsn(ACONST_NULL);
                        finalMethodVisitor.visitLabel(label2);
                        finalMethodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Integer"});
                        finalMethodVisitor.visitInsn(ARETURN);
                        finalMethodVisitor.visitMaxs(2, 1);
                        finalMethodVisitor.visitEnd();
                        nullPos++;
                    }

                    public void visitDouble(DoubleField field) throws Exception {
                        finalMethodVisitor.visitCode();
                        finalMethodVisitor.visitVarInsn(ALOAD, 0);
                        finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                                getNullFieldName(nullPos), "I");
                        finalMethodVisitor.visitLdcInsn(1 << getIndex(nullPos));
                        finalMethodVisitor.visitInsn(IAND);
                        Label label1 = new Label();
                        finalMethodVisitor.visitJumpInsn(IFNE, label1);
                        finalMethodVisitor.visitVarInsn(ALOAD, 0);
                        finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "D");
                        finalMethodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                        Label label2 = new Label();
                        finalMethodVisitor.visitJumpInsn(GOTO, label2);
                        finalMethodVisitor.visitLabel(label1);
                        finalMethodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                        finalMethodVisitor.visitInsn(ACONST_NULL);
                        finalMethodVisitor.visitLabel(label2);
                        finalMethodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Double"});
                        finalMethodVisitor.visitInsn(ARETURN);
                        finalMethodVisitor.visitMaxs(2, 1);
                        finalMethodVisitor.visitEnd();
                        nullPos++;
                    }

                    public void visitLong(LongField field) throws Exception {
                        finalMethodVisitor.visitCode();
                        finalMethodVisitor.visitVarInsn(ALOAD, 0);
                        finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                                getNullFieldName(nullPos), "I");
                        finalMethodVisitor.visitLdcInsn(1 << getIndex(nullPos));
                        finalMethodVisitor.visitInsn(IAND);
                        Label label1 = new Label();
                        finalMethodVisitor.visitJumpInsn(IFNE, label1);
                        finalMethodVisitor.visitVarInsn(ALOAD, 0);
                        finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "J");
                        finalMethodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                        Label label2 = new Label();
                        finalMethodVisitor.visitJumpInsn(GOTO, label2);
                        finalMethodVisitor.visitLabel(label1);
                        finalMethodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                        finalMethodVisitor.visitInsn(ACONST_NULL);
                        finalMethodVisitor.visitLabel(label2);
                        finalMethodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Long"});
                        finalMethodVisitor.visitInsn(ARETURN);
                        finalMethodVisitor.visitMaxs(2, 1);
                        finalMethodVisitor.visitEnd();
                        nullPos++;
                    }

                    public void notManaged(Field field) throws Exception {
                        finalMethodVisitor.visitCode();
                        finalMethodVisitor.visitVarInsn(ALOAD, 0);
                        finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                                getName(field), field.safeVisit(new FieldVisitorToVisitName().withUserType()).name);
                        finalMethodVisitor.visitInsn(ARETURN);
                        finalMethodVisitor.visitMaxs(1, 1);
                        finalMethodVisitor.visitEnd();
                    }
                });
            }
        }
        {
            org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor getVisitor = new org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor() {
                int nullPos = 0;

                public void visitInteger(IntegerField field) throws Exception {
                    MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "setNative_" + getName(field),
                            "(" + field.safeVisit(new FieldVisitorToVisitName().withWithNativeType()).name + ")V", null, null);
                    methodVisitor.visitCode();
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
                    methodVisitor.visitInsn(IAND);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitVarInsn(ILOAD, 1);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getName(field), "I");
                    methodVisitor.visitInsn(RETURN);
                    Label label3 = new Label();
                    methodVisitor.visitLabel(label3);
                    methodVisitor.visitMaxs(3, 2);
                    methodVisitor.visitEnd();
                    nullPos++;
                }

                public void visitDouble(DoubleField field) throws Exception {
                    MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "setNative_" + getName(field),
                            "(" + field.safeVisit(new FieldVisitorToVisitName().withWithNativeType()).name + ")V", null, null);
                    methodVisitor.visitCode();
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
                    methodVisitor.visitInsn(IAND);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitVarInsn(DLOAD, 1);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getName(field), "D");
                    methodVisitor.visitInsn(RETURN);
                    methodVisitor.visitMaxs(3, 3);
                    methodVisitor.visitEnd();
                    nullPos++;
                }

                public void visitLong(LongField field) throws Exception {
                    MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "setNative_" + getName(field),
                            "(" + field.safeVisit(new FieldVisitorToVisitName().withWithNativeType()).name + ")V", null, null);
                    methodVisitor.visitCode();
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
                    methodVisitor.visitInsn(IAND);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitVarInsn(LLOAD, 1);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getName(field), "J");
                    methodVisitor.visitInsn(RETURN);
                    methodVisitor.visitMaxs(3, 3);
                    methodVisitor.visitEnd();
                    nullPos++;
                }
            };
            for (Field field : fields) {
                field.safeVisit(getVisitor);
            }
        }
        {
            org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor setVisitor = new org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor() {
                int nullPos = 0;

                public void visitInteger(IntegerField field) throws Exception {
                    MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "set_" + getName(field),
                            "(Ljava/lang/Integer;)V", null, null);
                    methodVisitor.visitCode();
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    Label label1 = new Label();
                    methodVisitor.visitJumpInsn(IFNONNULL, label1);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(1 << getIndex(nullPos));
                    methodVisitor.visitInsn(IOR);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    Label label3 = new Label();
                    methodVisitor.visitJumpInsn(GOTO, label3);
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
                    methodVisitor.visitInsn(IAND);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "I");
                    methodVisitor.visitLabel(label3);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitInsn(RETURN);
                    methodVisitor.visitMaxs(3, 2);
                    methodVisitor.visitEnd();
                    nullPos++;
                }

                public void visitDouble(DoubleField field) throws Exception {
                    MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "set_" + getName(field),
                            "(Ljava/lang/Double;)V", null, null);
                    methodVisitor.visitCode();
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    Label label1 = new Label();
                    methodVisitor.visitJumpInsn(IFNONNULL, label1);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(1 << getIndex(nullPos));
                    methodVisitor.visitInsn(IOR);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    Label label3 = new Label();
                    methodVisitor.visitJumpInsn(GOTO, label3);
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
                    methodVisitor.visitInsn(IAND);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "D");
                    methodVisitor.visitLabel(label3);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitInsn(RETURN);
                    methodVisitor.visitMaxs(3, 2);
                    methodVisitor.visitEnd();
                    nullPos++;
                }

                public void visitLong(LongField field) throws Exception {
                    MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "set_" + getName(field),
                            "(Ljava/lang/Long;)V", null, null);
                    methodVisitor.visitCode();
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    Label label1 = new Label();
                    methodVisitor.visitJumpInsn(IFNONNULL, label1);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(1 << getIndex(nullPos));
                    methodVisitor.visitInsn(IOR);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    Label label3 = new Label();
                    methodVisitor.visitJumpInsn(GOTO, label3);
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
                    methodVisitor.visitInsn(IAND);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                            getNullFieldName(nullPos), "I");
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                    methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "J");
                    methodVisitor.visitLabel(label3);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitInsn(RETURN);
                    methodVisitor.visitMaxs(3, 2);
                    methodVisitor.visitEnd();
                    nullPos++;
                }
            };
            for (Field field : fields) {
                field.safeVisit(setVisitor);
            }
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "accept",
                    "(Lorg/globsframework/metamodel/fields/FieldValueVisitor;)Lorg/globsframework/metamodel/fields/FieldValueVisitor;",
                    "<T::Lorg/globsframework/metamodel/fields/FieldValueVisitor;>(TT;)TT;", new String[]{"java/lang/Exception"});
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "acceptValueStatic",
                    "(Lorg/globsframework/model/generated/GeneratedGlob_" + id + ";Lorg/globsframework/metamodel/fields/FieldValueVisitor;)Lorg/globsframework/metamodel/fields/FieldValueVisitor;", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "apply", "(Lorg/globsframework/model/FieldValues$Functor;)Lorg/globsframework/model/FieldValues$Functor;", "<T::Lorg/globsframework/model/FieldValues$Functor;>(TT;)TT;", new String[]{"java/lang/Exception"});
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "processValue",
                    "(Lorg/globsframework/model/generated/GeneratedGlob_" + id + ";Lorg/globsframework/model/FieldValues$Functor;)Lorg/globsframework/model/FieldValues$Functor;", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "getSetAccessor",
                    "(Lorg/globsframework/metamodel/Field;)Lorg/globsframework/model/globaccessor/set/GlobSetAccessor;", null, null);
            methodVisitor.visitCode();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/Field", "getIndex", "()I", true);

                Label[] labels = IntStream.range(0, fields.length).mapToObj(i -> new Label()).toArray(Label[]::new);

                Label label = new Label();
                methodVisitor.visitTableSwitchInsn(0, fields.length - 1, label, labels);

                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    methodVisitor.visitLabel(labels[i]);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field), "INSTANCE",
                            "Lorg/globsframework/model/generated/GeneratedGlob_" + id + "$" + SET_ACCESSOR + getName(field) + ";");
                    methodVisitor.visitInsn(ARETURN);
                }

                methodVisitor.visitLabel(label);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "GLOB_TYPE", "Lorg/globsframework/metamodel/GlobType;");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generated/GeneratedGlob_" + id, "throwError", "(Lorg/globsframework/metamodel/GlobType;Lorg/globsframework/metamodel/Field;)V", false);
            }
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "getGetAccessor",
                    "(Lorg/globsframework/metamodel/Field;)Lorg/globsframework/model/globaccessor/get/GlobGetAccessor;", null, null);
            methodVisitor.visitCode();

            if (fields.length != 0) {

                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/Field", "getIndex", "()I", true);

                Label[] labels = IntStream.range(0, fields.length).mapToObj(i -> new Label()).toArray(Label[]::new);

                Label label = new Label();
                methodVisitor.visitTableSwitchInsn(0, fields.length - 1, label, labels);

                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    methodVisitor.visitLabel(labels[i]);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field), "INSTANCE",
                            "Lorg/globsframework/model/generated/GeneratedGlob_" + id + "$" + GET_ACCESSOR + getName(field) + ";");
                    methodVisitor.visitInsn(ARETURN);
                }
                methodVisitor.visitLabel(label);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "GLOB_TYPE", "Lorg/globsframework/metamodel/GlobType;");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generated/GeneratedGlob_" + id, "throwError",
                        "(Lorg/globsframework/metamodel/GlobType;Lorg/globsframework/metamodel/Field;)V", false);
            }
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 1);
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

        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "org/globsframework/model/generated/GeneratedGlobFactory_" + id,
                null, "org/globsframework/model/generator/AbstractGeneratedGlobFactory", null);

        classWriter.visitInnerClass("org/globsframework/model/FieldValues$Functor", "org/globsframework/model/FieldValues",
                "Functor", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

        {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "GLOB_TYPE",
                    "Lorg/globsframework/metamodel/GlobType;", null, null);
            fieldVisitor.visitEnd();
        }

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();

        Field[] fields = globType.getFields();
        for (Field field : fields) {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, getName(field),
                    "Lorg/globsframework/metamodel/fields/" + field.safeVisit(visitor.withFieldType()).name + ";", null, null);
            fieldVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generator/AbstractGeneratedGlobFactory", "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 1);
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
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "accept", "(Lorg/globsframework/metamodel/fields/FieldVisitor;)Lorg/globsframework/metamodel/fields/FieldVisitor;",
                    "<T::Lorg/globsframework/metamodel/fields/FieldVisitor;>(TT;)TT;", new String[]{"java/lang/Exception"});
            methodVisitor.visitCode();
            for (Field field : fields) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                String type = field.safeVisit(visitor.withFieldType()).name;
                String method = field.safeVisit(visitor.withMethodVisitor()).name;
                methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, getName(field),
                        "Lorg/globsframework/metamodel/fields/" + type + ";");
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/fields/FieldVisitor",
                        method, "(Lorg/globsframework/metamodel/fields/" + type + ";)V", true);
            }

            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "accept",
                    "(Lorg/globsframework/metamodel/fields/FieldVisitorWithContext;Ljava/lang/Object;)Lorg/globsframework/metamodel/fields/FieldVisitorWithContext;",
                    "<T::Lorg/globsframework/metamodel/fields/FieldVisitorWithContext<TC;>;C:Ljava/lang/Object;>(TT;TC;)TT;", new String[]{"java/lang/Exception"});
            methodVisitor.visitCode();
            for (Field field : fields) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, getName(field),
                        "Lorg/globsframework/metamodel/fields/" + field.safeVisit(visitor.withFieldType()).name + ";");
                methodVisitor.visitVarInsn(ALOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/fields/FieldVisitorWithContext",
                        field.safeVisit(visitor.withMethodVisitor()).name,
                        "(Lorg/globsframework/metamodel/fields/" + field.safeVisit(visitor.withFieldType()).name + ";Ljava/lang/Object;)V", true);
            }
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(3, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "accept",
                    "(Lorg/globsframework/metamodel/fields/FieldVisitorWithTwoContext;Ljava/lang/Object;Ljava/lang/Object;)Lorg/globsframework/metamodel/fields/FieldVisitorWithTwoContext;",
                    "<T::Lorg/globsframework/metamodel/fields/FieldVisitorWithTwoContext<TC;TD;>;C:Ljava/lang/Object;D:Ljava/lang/Object;>(TT;TC;TD;)TT;", new String[]{"java/lang/Exception"});
            methodVisitor.visitCode();

            for (Field field : fields) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, getName(field),
                        "Lorg/globsframework/metamodel/fields/" + field.safeVisit(visitor.withFieldType()).name + ";");
                methodVisitor.visitVarInsn(ALOAD, 2);
                methodVisitor.visitVarInsn(ALOAD, 3);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/fields/FieldVisitorWithTwoContext",
                        field.safeVisit(visitor.withMethodVisitor()).name,
                        "(Lorg/globsframework/metamodel/fields/" + field.safeVisit(visitor.withFieldType()).name + ";Ljava/lang/Object;Ljava/lang/Object;)V", true);
            }
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(4, 4);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "processValue",
                    "(Lorg/globsframework/model/generated/GeneratedGlob_" + id + ";Lorg/globsframework/model/FieldValues$Functor;)Lorg/globsframework/model/FieldValues$Functor;",
                    "<T::Lorg/globsframework/model/FieldValues$Functor;>(Lorg/globsframework/model/generated/GeneratedGlob_" + id + ";TT;)TT;", new String[]{"java/lang/Exception"});
            methodVisitor.visitCode();
            for (Field field : fields) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, getName(field),
                        "Lorg/globsframework/metamodel/fields/" + field.safeVisit(visitor.withFieldType()).name + ";");
                methodVisitor.visitVarInsn(ALOAD, 0);
                MethodVisitor finalMethodVisitor = methodVisitor;
                field.safeVisit(new org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor() {
                    public void visitInteger(IntegerField field) throws Exception {
                        finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                                "get_" + getName(field), "()Ljava/lang/Integer;", false);
                    }

                    public void visitLong(LongField field) throws Exception {
                        finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                                "get_" + getName(field), "()Ljava/lang/Long;", false);
                    }

                    public void visitDouble(DoubleField field) throws Exception {
                        finalMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/globsframework/model/generated/GeneratedGlob_" + id,
                                "get_" + getName(field), "()Ljava/lang/Double;", false);
                    }

                    public void notManaged(Field field) throws Exception {
                        finalMethodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id,
                                getName(field), field.safeVisit(visitor.withWithNativeType()).name);
                    }
                });
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/model/FieldValues$Functor", "process",
                        "(Lorg/globsframework/metamodel/Field;Ljava/lang/Object;)V", true);
            }
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(4, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "acceptValueStatic",
                    "(Lorg/globsframework/model/generated/GeneratedGlob_" + id + ";Lorg/globsframework/metamodel/fields/FieldValueVisitor;)Lorg/globsframework/metamodel/fields/FieldValueVisitor;",
                    "<T::Lorg/globsframework/metamodel/fields/FieldValueVisitor;>(Lorg/globsframework/model/generated/GeneratedGlob_" + id + ";TT;)TT;", new String[]{"java/lang/Exception"});
            methodVisitor.visitCode();

            for (Field field : fields) {
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, getName(field),
                        "Lorg/globsframework/metamodel/fields/" + field.safeVisit(visitor.withFieldType()).name + ";");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field),
                        field.safeVisit(visitor.withWithNativeType()).name);
                MethodVisitor finalMethodVisitor = methodVisitor;
                field.safeVisit(new org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor() {
                    public void visitInteger(IntegerField field) throws Exception {
                        finalMethodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    }

                    public void visitLong(LongField field) throws Exception {
                        finalMethodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    }

                    public void visitDouble(DoubleField field) throws Exception {
                        finalMethodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    }
                });
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/fields/FieldValueVisitor",
                        field.safeVisit(visitor.withMethodVisitor()).name,
                        "(Lorg/globsframework/metamodel/fields/" + field.safeVisit(visitor.withFieldType()).name + ";" +
                                field.safeVisit(visitor.withUserType()).name + ")V", true);
            }

            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(4, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getSetValueAccessor", "(Lorg/globsframework/metamodel/Field;)Lorg/globsframework/model/globaccessor/set/GlobSetAccessor;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generated/GeneratedGlob_" + id, "getSetAccessor", "(Lorg/globsframework/metamodel/Field;)Lorg/globsframework/model/globaccessor/set/GlobSetAccessor;", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getGetValueAccessor", "(Lorg/globsframework/metamodel/Field;)Lorg/globsframework/model/globaccessor/get/GlobGetAccessor;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generated/GeneratedGlob_" + id, "getGetAccessor", "(Lorg/globsframework/metamodel/Field;)Lorg/globsframework/model/globaccessor/get/GlobGetAccessor;", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generator/AsmGlobGenerator", "TYPE", "Lorg/globsframework/metamodel/GlobType;");
            methodVisitor.visitFieldInsn(PUTSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "GLOB_TYPE", "Lorg/globsframework/metamodel/GlobType;");

            for (Field field : fields) {
                methodVisitor.visitFieldInsn(GETSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, "GLOB_TYPE", "Lorg/globsframework/metamodel/GlobType;");
                methodVisitor.visitLdcInsn(field.getName());
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/GlobType", "getField", "(Ljava/lang/String;)Lorg/globsframework/metamodel/Field;", true);
                String typeName = field.safeVisit(visitor.withFieldType()).name;
                methodVisitor.visitTypeInsn(CHECKCAST, "org/globsframework/metamodel/fields/" + typeName);
                methodVisitor.visitFieldInsn(PUTSTATIC, "org/globsframework/model/generated/GeneratedGlobFactory_" + id, getName(field), "Lorg/globsframework/metamodel/fields/" + typeName + ";");
            }
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 0);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

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
        SpecificName characteristic;
        public String name;
        public boolean isArray;

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
                case outputTypeSimple:
                case nativeType:
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

    private static class CountNeedNullFieldVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        int count = 0;

        public void visitInteger(IntegerField field) throws Exception {
            count++;
        }

        public void visitDouble(DoubleField field) throws Exception {
            count++;
        }

        public void visitLong(LongField field) throws Exception {
            count++;
        }

//        public void visitBoolean(BooleanField field) throws Exception {
        //            count++;
//        }

//        public void visitDate(DateField field) throws Exception {
//            count++;
//        }
//
//        public void visitDateTime(DateTimeField field) throws Exception {
//            count++;
//        }
    }

    private static class SetFieldVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;
        private final FieldVisitorToVisitName visitor;
        private int nullPos = 0;

        public SetFieldVisitor(MethodVisitor methodVisitor, int id, FieldVisitorToVisitName visitor) {
            this.methodVisitor = methodVisitor;
            this.id = id;
            this.visitor = visitor;
        }

        public void visitInteger(IntegerField field) throws Exception {
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "I");
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getNullFieldName(nullPos), "I");
            methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
            methodVisitor.visitInsn(IAND);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getNullFieldName(nullPos), "I");
            nullPos++;
        }

        public void visitLong(LongField field) throws Exception {
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "J");
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getNullFieldName(nullPos), "I");
            methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
            methodVisitor.visitInsn(IAND);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getNullFieldName(nullPos), "I");
            nullPos++;
        }

        public void visitDouble(DoubleField field) throws Exception {
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), "D");
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getNullFieldName(nullPos), "I");
            methodVisitor.visitLdcInsn(~(1 << getIndex(nullPos)));
            methodVisitor.visitInsn(IAND);
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getNullFieldName(nullPos), "I");
            nullPos++;
        }

        public void notManaged(Field field) throws Exception {
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 2);
            FieldVisitorToVisitName fieldVisitorToVisitName = field.safeVisit(visitor.withSimpleUserType());
            methodVisitor.visitTypeInsn(CHECKCAST, fieldVisitorToVisitName.name);
            if (fieldVisitorToVisitName.isArray) {
                methodVisitor.visitTypeInsn(CHECKCAST, fieldVisitorToVisitName.name);
            }
            methodVisitor.visitFieldInsn(PUTFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), field.safeVisit(visitor.withUserType()).name);
        }
    }

    private static void generateSetNull(String className, GlobType globType, ClassWriter cw) {
        MethodVisitor cv;
        cv = cw.visitMethod(ACC_PRIVATE, "setNull", "(Lorg/globsframework/metamodel/Field;)V", null, null);
        cv.visitCode();
        cv.visitVarInsn(ALOAD, 1);
        cv.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/metamodel/Field", "getIndex", "()I", true);

        Label lEnd = new Label();
        Label[] labels = new Label[globType.getFieldCount()];
        int i = 0;
        for (Field field : globType.getFields()) {
            labels[i] = new Label();
            i++;
        }

        Label lThrow = new Label();

        cv.visitTableSwitchInsn(0, globType.getFieldCount() - 1, lThrow, labels);

        SetNullFieldVisitor setNullFieldVisitor = new SetNullFieldVisitor(className, cv);
        i = 0;
        for (Field field : globType.getFields()) {
            cv.visitLabel(labels[i]);
            cv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            cv.visitVarInsn(ALOAD, 0);
            field.safeVisit(setNullFieldVisitor);
            cv.visitJumpInsn(GOTO, lEnd);
            ++i;
        }
        cv.visitLabel(lThrow);
        cv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        cv.visitVarInsn(ALOAD, 0);
        cv.visitVarInsn(ALOAD, 1);
        cv.visitMethodInsn(INVOKEVIRTUAL, className, "throwError",
                "(Lorg/globsframework/metamodel/Field;)V", false);
        cv.visitLabel(lEnd);
        cv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        cv.visitInsn(RETURN);
        cv.visitMaxs(3, 2);
    }

    private static class SetNullFieldVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private int nullPos = 0;
        private String className;
        private MethodVisitor cv;

        public SetNullFieldVisitor(String className, MethodVisitor cv) {
            this.className = className;
            this.cv = cv;
        }

        public void visitInteger(IntegerField field) {
            setNullFlag(field);
        }

        public void visitLong(LongField field) {
            setNullFlag(field);
        }

        public void visitDouble(DoubleField field) {
            setNullFlag(field);
        }

        private void setNullFlag(Field field) {
            cv.visitInsn(DUP);
            cv.visitFieldInsn(GETFIELD, className, getNullFieldName(nullPos), "I");
            cv.visitLdcInsn(1 << getIndex(nullPos));
            cv.visitInsn(IOR);
            cv.visitFieldInsn(PUTFIELD, className, getNullFieldName(nullPos), "I");
            nullPos++;
        }

        public void notManaged(Field field) {
            cv.visitInsn(ACONST_NULL);
            cv.visitFieldInsn(PUTFIELD, className, getName(field),
                    field.safeVisit(new FieldVisitorToVisitName().withUserType()).name);
        }
    }


    private static class GetWithNullFieldVisitor extends org.globsframework.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;
        private final FieldVisitorToVisitName visitor;
        private int nullPos = 0;

        public GetWithNullFieldVisitor(MethodVisitor methodVisitor, int id, FieldVisitorToVisitName visitor) {
            this.methodVisitor = methodVisitor;
            this.id = id;
            this.visitor = visitor;
        }

        public void visitInteger(IntegerField field) throws Exception {
            generateGet(getName(field), "I", "java/lang/Integer");
            nullPos++;
        }

        public void visitLong(LongField field) throws Exception {
            generateGet(getName(field), "J", "java/lang/Long");
            nullPos++;
        }

        public void visitDouble(DoubleField field) throws Exception {
            generateGet(getName(field), "D", "java/lang/Double");
            nullPos++;
        }

        private void generateGet(String name, String nativeType, String objectType) {
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getNullFieldName(nullPos), "I");
            methodVisitor.visitLdcInsn(1 << getIndex(nullPos));
            methodVisitor.visitInsn(IAND);
            Label label1 = new Label();
            methodVisitor.visitJumpInsn(IFNE, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, name, nativeType);
            methodVisitor.visitMethodInsn(INVOKESTATIC, objectType, "valueOf", "(" + nativeType + ")L" + objectType + ";", false);
            Label label2 = new Label();
            methodVisitor.visitJumpInsn(GOTO, label2);
            methodVisitor.visitLabel(label1);
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitLabel(label2);
            methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{objectType});
            methodVisitor.visitInsn(ARETURN);
        }

        public void notManaged(Field field) throws Exception {
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, "org/globsframework/model/generated/GeneratedGlob_" + id, getName(field), field.safeVisit(visitor.withUserType()).name);
            methodVisitor.visitInsn(ARETURN);
        }
    }
}
