package org.globsframework.model.generator.object;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.GlobFactory;
import org.globsframework.core.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.model.generator.AbstractGeneratedGlobFactory;
import org.globsframework.model.generator.AccessorProvider;
import org.globsframework.model.generator.DoGetAccessorProvider;
import org.globsframework.model.generator.AsmAccessorGenerator;
import org.globsframework.model.generator.AsmFactoryGenerator;
import org.globsframework.model.generator.FieldVisitorToVisitName;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;


public class AsmGlobObjectGenerator {
    public static final Pattern COMPILE = Pattern.compile("[^\\w]");
    static AtomicInteger ID = new AtomicInteger();
    // What the generated factory reads while it is being built : its <clinit> calls getType(id) and its
    // <init> getAccessors(id). The entry lives only for the duration of create, so nothing here keeps a
    // GlobType -- or the throwaway ClassLoader the provider closes over -- alive.
    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(GlobType type, AccessorProvider accessors) {
    }

    public static GlobFactory create(GlobType globType) {
        return create(globType, true);
    }

    /**
     * @param generateAccessors when false the factory gets the doGet/doSet-based accessors instead of one
     *                          generated class per field and direction. See GenerationOption.
     */
    public static GlobFactory create(GlobType globType, boolean generateAccessors) {
        try {
            int id = ID.incrementAndGet();
            ClassLoader bytesClassloader = new ClassLoader(AsmGlobObjectGenerator.class.getClassLoader()) {
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    String internalName = name.replace('.', '/');
                    if (internalName.equalsIgnoreCase(getGeneratedGlobFactoryName(id))) {
                        byte[] b = generateFactory(globType, id);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    } else if (internalName.equalsIgnoreCase(getGeneratedGlobName(id))) {
                        byte[] b = generateGlob(id, globType);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    }
                    String globName = getGeneratedGlobName(id);
                    for (Field field : globType.getFields()) {
                        byte[] b = null;
                        if (internalName.equals(AsmAccessorGenerator.getGetAccessorName(globName, field.getIndex()))) {
                            b = AsmAccessorGenerator.generateGet(globName, getFieldName(field), field, false,
                                    globType.getFieldCount() <= 32);
                        } else if (internalName.equals(AsmAccessorGenerator.getSetAccessorName(globName, field.getIndex()))) {
                            b = AsmAccessorGenerator.generateSet(globName, getFieldName(field), field, false);
                        }
                        if (b != null) {
                            return super.defineClass(name.replace("/", "."), b, 0, b.length);
                        }
                    }
                    return super.findClass(name);
                }
            };
            PENDING.put(id, new Pending(globType, generateAccessors
                    ? AsmAccessorGenerator.providerFor(bytesClassloader, getGeneratedGlobName(id))
                    : new DoGetAccessorProvider()));
            try {
                // newInstance triggers <clinit> then <init>, the two that read PENDING : the accessor
                // classes, and through them the Glob class, are loaded from inside that constructor.
                return (GlobFactory) bytesClassloader.loadClass(getGeneratedGlobFactoryName(id))
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Throwable e) {
                throw new RuntimeException("fail ", e);
            } finally {
                PENDING.remove(id);
            }
        } catch (Throwable e) {
            String mes = "Can not generate bytecode for " + globType.describe() + " : " + e.getMessage();
            throw new RuntimeException(mes, e);
        }
    }

    private static String getFieldName(Field field) {
        return COMPILE.matcher(field.getName()).replaceAll("_");
    }

    /**
     * The guard of the unrolled visitors : reads the isSet mask of the Glob in slot 0 and jumps to skip
     * when the bit of this field is clear. Inlined at both widths — the long one used to go through an
     * INVOKEVIRTUAL isSetAt(index) for no reason. Costs 4 stack slots instead of 2, hence maskStack.
     */
    private static void jumpIfNotSet(MethodVisitor methodVisitor, int id, Field field, boolean is32Bit, Label skip) {
        methodVisitor.visitVarInsn(ALOAD, 0);
        if (is32Bit) {
            methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(id), "isSet", "I");
            methodVisitor.visitLdcInsn(1 << field.getIndex());
            methodVisitor.visitInsn(IAND);
        } else {
            methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(id), "isSet", "J");
            methodVisitor.visitLdcInsn(1L << field.getIndex());
            methodVisitor.visitInsn(LAND);
            methodVisitor.visitInsn(LCONST_0);
            methodVisitor.visitInsn(LCMP);
        }
        methodVisitor.visitJumpInsn(IFEQ, skip);
    }

    private static int maskStack(boolean is32Bit) {
        return is32Bit ? 2 : 4;
    }

    public static byte[] generateGlob(int id, GlobType globType) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;

        boolean is32Bit = globType.getFieldCount() <= 32;
        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, getGeneratedGlobName(id), null,
                "org/globsframework/model/generator/object/AbstractGeneratedGlob" + (is32Bit ? "32" : "64"), null);


        Field[] fields = globType.getFields();

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();
        {
            for (Field field : fields) {
                // public, not private : the generated accessor classes GETFIELD/PUTFIELD them directly
                fieldVisitor = classWriter.visitField(ACC_PUBLIC, getFieldName(field), field.safeAccept(visitor.withOutputType()).name, null, null);
                fieldVisitor.visitEnd();
            }
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generator/object/AbstractGeneratedGlob" + (is32Bit ? "32" : "64"), "<init>", "()V", false);

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "accept", "(Lorg/globsframework/core/metamodel/fields/FieldValueVisitor;)Lorg/globsframework/core/metamodel/fields/FieldValueVisitor;", "<T::Lorg/globsframework/core/metamodel/fields/FieldValueVisitor;>(TT;)TT;", new String[] { "java/lang/Exception" });
            methodVisitor.visitCode();

            for (int i = 0; i < fields.length; i++) {
                Label label3 = new Label();
                Field field = fields[i];
                jumpIfNotSet(methodVisitor, id, field, is32Bit, label3);
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
            methodVisitor.visitMaxs(Math.max(3, maskStack(is32Bit)), 2);
            methodVisitor.visitEnd();
        }
        {
            // <CTX, T extends FieldValueVisitorWithContext<CTX>> T accept(T functor, CTX ctx)
            // the context is just an extra reference argument forwarded to every visit call
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "accept",
                    "(Lorg/globsframework/core/metamodel/fields/FieldValueVisitorWithContext;Ljava/lang/Object;)Lorg/globsframework/core/metamodel/fields/FieldValueVisitorWithContext;",
                    "<CTX:Ljava/lang/Object;T::Lorg/globsframework/core/metamodel/fields/FieldValueVisitorWithContext<TCTX;>;>(TT;TCTX;)TT;",
                    new String[]{"java/lang/Exception"});
            methodVisitor.visitCode();

            for (Field field : fields) {
                Label skip = new Label();
                jumpIfNotSet(methodVisitor, id, field, is32Bit, skip);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(id), getFieldName(field),
                        "Lorg/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name + ";");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(id), getFieldName(field),
                        field.safeAccept(visitor.withOutputType()).name);
                methodVisitor.visitVarInsn(ALOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/core/metamodel/fields/FieldValueVisitorWithContext",
                        field.safeAccept(visitor.withMethodVisitor()).name, "(Lorg/globsframework/core/metamodel/fields/"
                                + field.safeAccept(visitor.withFieldType()).name + ";"
                                + field.safeAccept(visitor.withOutputType()).name
                                + "Ljava/lang/Object;)V",
                        true);
                methodVisitor.visitLabel(skip);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(Math.max(4, maskStack(is32Bit)), 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "apply", "(Lorg/globsframework/core/model/FieldValues$Functor;)Lorg/globsframework/core/model/FieldValues$Functor;", "<T::Lorg/globsframework/core/model/FieldValues$Functor;>(TT;)TT;", new String[] { "java/lang/Exception" });
            methodVisitor.visitCode();

            for (int i = 0; i < fields.length; i++) {
                Label label3 = new Label();
                Field field = fields[i];
                jumpIfNotSet(methodVisitor, id, field, is32Bit, label3);
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
            methodVisitor.visitMaxs(Math.max(3, maskStack(is32Bit)), 2);
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

    /**
     * Called from the generated factory's {@code <clinit>}, which runs while create is still on the stack.
     * Replaces the former static TYPE single-slot channel, so create no longer has to be synchronized.
     */
    public static GlobType getType(int id) {
        return pending(id).type();
    }

    /** Called from the generated factory's {@code <init>}, and handed straight to the super constructor. */
    public static AccessorProvider getAccessors(int id) {
        return pending(id).accessors();
    }

    private static Pending pending(int id) {
        Pending pending = PENDING.get(id);
        if (pending == null) {
            throw new IllegalStateException("Nothing registered for generated factory " + id
                                            + " : the generated class was initialized outside of create.");
        }
        return pending;
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
                null, "org/globsframework/model/generator/AbstractGeneratedGlobFactory", null);

        {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "TYPE",
                    "Lorg/globsframework/core/metamodel/GlobType;", null, null);
            fieldVisitor.visitEnd();
        }
        AsmFactoryGenerator.generateFieldConstants(classWriter, globType.getFields());
        AsmFactoryGenerator.generateAccepts(classWriter, getGeneratedGlobFactoryName(id), globType.getFields());

        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(id), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
            methodVisitor.visitLdcInsn(id);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generator/object/AsmGlobObjectGenerator",
                    "getAccessors", "(I)Lorg/globsframework/model/generator/AccessorProvider;", false);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generator/AbstractGeneratedGlobFactory",
                    "<init>", "(Lorg/globsframework/core/metamodel/GlobType;Lorg/globsframework/model/generator/AccessorProvider;)V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(3, 1);
            methodVisitor.visitEnd();
        }
        {
            // GlobFactory.create takes a context since globs 5.8 : the descriptor must match or the
            // inherited DefaultGlobFactory.create(Object) silently wins and no generated Glob is ever built.
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "create", "(Ljava/lang/Object;)Lorg/globsframework/core/model/MutableGlob;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitTypeInsn(NEW, getGeneratedGlobName(id));
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, getGeneratedGlobName(id), "<init>", "()V", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitLdcInsn(id);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generator/object/AsmGlobObjectGenerator",
                    "getType", "(I)Lorg/globsframework/core/metamodel/GlobType;", false);
            methodVisitor.visitFieldInsn(PUTSTATIC, getGeneratedGlobFactoryName(id), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");

            AsmFactoryGenerator.generateFieldConstantsInit(methodVisitor, getGeneratedGlobFactoryName(id),
                    globType.getFields());

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
