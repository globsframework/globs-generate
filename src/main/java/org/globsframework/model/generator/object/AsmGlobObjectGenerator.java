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
import org.globsframework.model.generator.AsmCallerGenerator;
import org.globsframework.model.generator.AsmFactoryGenerator;
import org.globsframework.model.generator.FieldVisitorToVisitName;
import org.globsframework.model.generator.GeneratedClassLoader;
import org.globsframework.model.generator.GeneratedName;
import org.globsframework.core.model.generate.read.GenerateCaller;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;


public class AsmGlobObjectGenerator {
    public static final Pattern COMPILE = Pattern.compile("[^\\w]");
    private static final String PACKAGE = "org/globsframework/gen/obj/";
    // What the generated factory reads while it is being built : its <clinit> calls getType(key) and its
    // <init> getAccessors(key), passing back the family key LDC'd into its own bytes -- half of its own
    // name, so nothing here varies from one run to the next. The entry lives only for the duration of
    // create, so nothing here keeps a GlobType alive past the generation that registered it.
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(GlobType type, AccessorProvider accessors, GenerateCaller callers) {
    }

    public static GlobFactory create(GlobType globType) {
        return create(globType, true);
    }

    /**
     * Generates the object-flavour Glob class of a type and its factory, and returns that factory.
     *
     * @param generateAccessors when false the factory gets the doGet/doSet-based accessors instead of one
     *                          generated class per field and direction. See GenerationOption.
     */
    public static GlobFactory create(GlobType globType, boolean generateAccessors) {
        try {
            String key = familyKey(globType, generateAccessors);
            String globName = getGeneratedGlobName(key);
            String factoryName = getGeneratedGlobFactoryName(key);
            GeneratedClassLoader loader = GeneratedClassLoader.get();

            // said, not generated : the bytes are built when each class is first asked for, and the Glob's
            // are, at the earliest, inside the factory's constructor
            loader.emit(factoryName, () -> generateFactory(globType, key));
            loader.emit(globName, () -> generateGlob(key, globType));
            if (generateAccessors) {
                // only then : with the doGet-based accessors nothing ever loads these, and an emitter that
                // is never used would hold on to the GlobType it closes over
                for (Field field : globType.getFields()) {
                    loader.emit(AsmAccessorGenerator.getGetAccessorName(globName, field.getIndex()),
                            () -> AsmAccessorGenerator.generateGet(globName, getFieldName(field), field, false,
                                    globType.getFieldCount() <= 32));
                    loader.emit(AsmAccessorGenerator.getSetAccessorName(globName, field.getIndex()),
                            () -> AsmAccessorGenerator.generateSet(globName, getFieldName(field), field, false));
                }
            }

            PENDING.put(key, new Pending(globType, generateAccessors
                    ? AsmAccessorGenerator.providerFor(loader, globName)
                    : new DoGetAccessorProvider(),
                    AsmCallerGenerator.generatorFor(loader, globName, globType, false)));
            try {
                // newInstance triggers <clinit> then <init>, the two that read PENDING : the accessor
                // classes, and through them the Glob class, are loaded from inside that constructor.
                return (GlobFactory) loader.load(factoryName)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Throwable e) {
                throw new RuntimeException("fail ", e);
            } finally {
                PENDING.remove(key);
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
    private static void jumpIfNotSet(MethodVisitor methodVisitor, String key, Field field, boolean is32Bit, Label skip) {
        methodVisitor.visitVarInsn(ALOAD, 0);
        if (is32Bit) {
            methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(key), "isSet", "I");
            methodVisitor.visitLdcInsn(1 << field.getIndex());
            methodVisitor.visitInsn(IAND);
        } else {
            methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(key), "isSet", "J");
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

    public static byte[] generateGlob(String key, GlobType globType) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;

        boolean is32Bit = globType.getFieldCount() <= 32;
        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, getGeneratedGlobName(key), null,
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
                jumpIfNotSet(methodVisitor, key, field, is32Bit, label3);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(key), getFieldName(field),
                        "Lorg/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name +";");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(key), getFieldName(field), field.safeAccept(visitor.withOutputType()).name);
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
                jumpIfNotSet(methodVisitor, key, field, is32Bit, skip);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(key), getFieldName(field),
                        "Lorg/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name + ";");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(key), getFieldName(field),
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
                jumpIfNotSet(methodVisitor, key, field, is32Bit, label3);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(key), getFieldName(field),
                        "Lorg/globsframework/core/metamodel/fields/" + field.safeAccept(visitor.withFieldType()).name +";");
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(key), getFieldName(field), field.safeAccept(visitor.withOutputType()).name);
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
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(key), "setSetAt", "(I)V", false);
                methodVisitor.visitVarInsn(ILOAD, 3);

                Label[] labels = IntStream.range(0, fields.length).mapToObj(i -> new Label()).toArray(Label[]::new);

                Label defaultLabel = new Label();
                methodVisitor.visitTableSwitchInsn(0, fields.length - 1, defaultLabel, labels);

                SetFieldVisitor setFieldVisitor = new SetFieldVisitor(methodVisitor, key, visitor);
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
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(key),
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
            methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(key), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
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
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(key), "isSetAt", "(I)Z", false);
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
                    field.safeAccept(new GenerateGetVisitor(methodVisitor, key));
                    methodVisitor.visitJumpInsn(GOTO, returnLabel);
                }

                methodVisitor.visitLabel(defaultLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, getGeneratedGlobName(key), "throwError", "(Lorg/globsframework/core/metamodel/fields/Field;)V", false);
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
    public static GlobType getType(String key) {
        return pending(key).type();
    }

    /** Called from the generated factory's {@code <init>}, and handed straight to the super constructor. */
    public static AccessorProvider getAccessors(String key) {
        return pending(key).accessors();
    }

    /** Called from the generated factory's {@code <init>} too, and handed straight to the super constructor. */
    public static GenerateCaller getCallerGenerator(String key) {
        return pending(key).callers();
    }

    private static Pending pending(String key) {
        Pending pending = PENDING.get(key);
        if (pending == null) {
            throw new IllegalStateException("Nothing registered for generated factory " + key
                                            + " : the generated class was initialized outside of create.");
        }
        return pending;
    }

    /**
     * The key the whole family shares : the Glob, its factory and its accessors are named from it, and it is
     * what the generated {@code <clinit>} and {@code <init>} pass back to reach their PENDING entry. Readable
     * half plus a digest of everything the emitted bytes depend on — see {@link GeneratedName}.
     * <p>
     * The accessors flag is in there although it changes no byte : it changes what the factory is handed, so
     * two families built from the same type with different options are two families, not one asked twice.
     */
    private static String familyKey(GlobType globType, boolean generateAccessors) {
        StringBuilder layout = new StringBuilder();
        for (Field field : globType.getFields()) {
            layout.append(field.getIndex()).append(':').append(getFieldName(field)).append(':')
                    .append(field.getDataType()).append(';');
        }
        return GeneratedName.family(new String[]{GeneratedName.simpleName(globType.getName())},
                "object", globType.getName(), Boolean.toString(generateAccessors), layout.toString());
    }

    private static String getGeneratedGlobName(String key) {
        return PACKAGE + "Glob_" + key;
    }

    private static String getGeneratedGlobFactoryName(String key) {
        return PACKAGE + "Factory_" + key;
    }


    public static byte[] generateFactory(GlobType globType, String key) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();

        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, getGeneratedGlobFactoryName(key),
                null, "org/globsframework/model/generator/AbstractGeneratedGlobFactory", null);

        {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "TYPE",
                    "Lorg/globsframework/core/metamodel/GlobType;", null, null);
            fieldVisitor.visitEnd();
        }
        AsmFactoryGenerator.generateFieldConstants(classWriter, globType.getFields());
        AsmFactoryGenerator.generateAccepts(classWriter, getGeneratedGlobFactoryName(key), globType.getFields());

        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETSTATIC, getGeneratedGlobFactoryName(key), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
            methodVisitor.visitLdcInsn(key);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generator/object/AsmGlobObjectGenerator",
                    "getAccessors", "(Ljava/lang/String;)Lorg/globsframework/model/generator/AccessorProvider;", false);
            methodVisitor.visitLdcInsn(key);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generator/object/AsmGlobObjectGenerator",
                    "getCallerGenerator", "(Ljava/lang/String;)Lorg/globsframework/core/model/generate/read/GenerateCaller;", false);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generator/AbstractGeneratedGlobFactory",
                    "<init>", "(Lorg/globsframework/core/metamodel/GlobType;Lorg/globsframework/model/generator/AccessorProvider;Lorg/globsframework/core/model/generate/read/GenerateCaller;)V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(4, 1);
            methodVisitor.visitEnd();
        }
        {
            // GlobFactory.create takes a context since globs 5.8 : the descriptor must match or the
            // inherited DefaultGlobFactory.create(Object) silently wins and no generated Glob is ever built.
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "create", "(Ljava/lang/Object;)Lorg/globsframework/core/model/MutableGlob;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitTypeInsn(NEW, getGeneratedGlobName(key));
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, getGeneratedGlobName(key), "<init>", "()V", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitLdcInsn(key);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generator/object/AsmGlobObjectGenerator",
                    "getType", "(Ljava/lang/String;)Lorg/globsframework/core/metamodel/GlobType;", false);
            methodVisitor.visitFieldInsn(PUTSTATIC, getGeneratedGlobFactoryName(key), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");

            AsmFactoryGenerator.generateFieldConstantsInit(methodVisitor, getGeneratedGlobFactoryName(key),
                    globType.getFields());

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(globType.getFieldCount() + 1, 0);
            methodVisitor.visitEnd();
        }
        return classWriter.toByteArray();
    }

    private static class SetFieldVisitor extends org.globsframework.core.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final String key;
        private final FieldVisitorToVisitName visitor;

        public SetFieldVisitor(MethodVisitor methodVisitor, String key, FieldVisitorToVisitName visitor) {
            this.methodVisitor = methodVisitor;
            this.key = key;
            this.visitor = visitor;
        }

        public void notManaged(Field field) {
            methodVisitor.visitTypeInsn(CHECKCAST, field.safeAccept(visitor.withSimpleUserType()).name);
            methodVisitor.visitFieldInsn(PUTFIELD, getGeneratedGlobName(key), getFieldName(field), field.safeAccept(visitor.withOutputType()).name);
        }
    }

    private static class GenerateGetVisitor extends org.globsframework.core.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final String key;

        public GenerateGetVisitor(MethodVisitor methodVisitor, String key) {
            this.methodVisitor = methodVisitor;
            this.key = key;
        }

        public void notManaged(Field field) {
            methodVisitor.visitFieldInsn(GETFIELD, getGeneratedGlobName(key),
                    getFieldName(field), field.safeAccept(new FieldVisitorToVisitName().withOutputType()).name);
        }
    }
}
