package org.globsframework.model.generator.primitive;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.GlobFactory;
import org.globsframework.model.generator.AccessorProvider;
import org.globsframework.model.generator.AsmAccessorGenerator;
import org.globsframework.model.generator.AsmFactoryGenerator;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;

public class AsmGlobPrimitiveGenerator {
    public static final Pattern COMPILE = Pattern.compile("[^\\w]");
    static AtomicInteger ID = new AtomicInteger();
    // What the generated factory reads while it is being built : its <clinit> calls getType(id) and its
    // <init> getAccessors(id). The entry lives only for the duration of create, so nothing here keeps a
    // GlobType -- or the throwaway ClassLoader the provider closes over -- alive.
    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(GlobType type, AccessorProvider accessors) {
    }

    public static GlobFactory create(GlobType globType) {
        try {
            int id = ID.incrementAndGet();
            ClassLoader bytesClassloader = new ClassLoader(AsmGlobPrimitiveGenerator.class.getClassLoader()) {
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    String internalName = name.replace('.', '/');
                    if (internalName.equalsIgnoreCase(getGlobFactoryName(id))) {
                        byte[] b = generateFactory(globType, id);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    } else if (internalName.equalsIgnoreCase(GenerateSetNullVisitor.getGlobName(id))) {
                        byte[] b = generateGlob(id, globType);
                        return super.defineClass(name.replace("/", "."), b, 0, b.length);
                    }
                    String globName = GenerateSetNullVisitor.getGlobName(id);
                    for (Field field : globType.getFields()) {
                        byte[] b = null;
                        if (internalName.equals(AsmAccessorGenerator.getGetAccessorName(globName, field.getIndex()))) {
                            b = AsmAccessorGenerator.generateGet(globName, getFieldName(field), field, true,
                                    globType.getFieldCount() <= 32);
                        } else if (internalName.equals(AsmAccessorGenerator.getSetAccessorName(globName, field.getIndex()))) {
                            b = AsmAccessorGenerator.generateSet(globName, getFieldName(field), field, true);
                        }
                        if (b != null) {
                            return super.defineClass(name.replace("/", "."), b, 0, b.length);
                        }
                    }
                    return super.findClass(name);
                }
            };
            PENDING.put(id, new Pending(globType,
                    AsmAccessorGenerator.providerFor(bytesClassloader, GenerateSetNullVisitor.getGlobName(id))));
            try {
                // newInstance triggers <clinit> then <init>, the two that read PENDING : the accessor
                // classes, and through them the Glob class, are loaded from inside that constructor.
                return (GlobFactory) bytesClassloader.loadClass(getGlobFactoryName(id))
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

    private static String getGlobFactoryName(int id) {
        return "org/globsframework/model/generated/primitive/GeneratedGlobFactory_" + id;
    }

    private static int getIndex(int pos) {
        return pos - 32 * (int) (pos / 32);
    }

    private static String getFieldName(Field field) {
        return COMPILE.matcher(field.getName()).replaceAll("_");
    }

    private static final String FIELDS = "org/globsframework/core/metamodel/fields/";

    /** Jumps to target when the index-th bit of the mask of the Glob in slot 0 matches : IFEQ clear, IFNE set. */
    private static void jumpOnMaskBit(MethodVisitor methodVisitor, int id, String mask, int index,
                                      boolean is32Bit, int jumpOpcode, Label target) {
        methodVisitor.visitVarInsn(ALOAD, 0);
        if (is32Bit) {
            methodVisitor.visitFieldInsn(GETFIELD, GenerateSetNullVisitor.getGlobName(id), mask, "I");
            methodVisitor.visitLdcInsn(1 << index);
            methodVisitor.visitInsn(IAND);
        } else {
            methodVisitor.visitFieldInsn(GETFIELD, GenerateSetNullVisitor.getGlobName(id), mask, "J");
            methodVisitor.visitLdcInsn(1L << index);
            methodVisitor.visitInsn(LAND);
            methodVisitor.visitInsn(LCONST_0);
            methodVisitor.visitInsn(LCMP);
        }
        methodVisitor.visitJumpInsn(jumpOpcode, target);
    }

    /**
     * visitor (slot 1), the field constant, then the boxed field value or null, then the call.
     * The GETSTATIC always uses the concrete *Field type; the call descriptor is the declared one, which
     * for FieldValues.Functor.process is (Field, Object) rather than the typed pair of a FieldValueVisitor.
     */
    private static void emitVisit(MethodVisitor methodVisitor, int id, Field field, FieldVisitorToVisitName visitor,
                                  Signature signature, boolean nullValue) {
        String constDesc = "L" + FIELDS + field.safeAccept(visitor.withFieldType()).name + ";";
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitFieldInsn(GETSTATIC, getGlobFactoryName(id), getFieldName(field), constDesc);
        if (nullValue) {
            methodVisitor.visitInsn(ACONST_NULL);
        } else {
            methodVisitor.visitVarInsn(ALOAD, 0);
            field.safeAccept(new GenerateGetVisitor(methodVisitor, id));
        }
        if (signature.withContext) {
            methodVisitor.visitVarInsn(ALOAD, 2);
        }
        String fieldParam = signature.fieldParamDesc == null ? constDesc : signature.fieldParamDesc;
        String valueParam = signature.valueParamDesc == null
                ? field.safeAccept(visitor.withUserType()).name : signature.valueParamDesc;
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, signature.itf,
                signature.methodName == null ? field.safeAccept(visitor.withMethodVisitor()).name : signature.methodName,
                "(" + fieldParam + valueParam + (signature.withContext ? "Ljava/lang/Object;" : "") + ")V", true);
    }

    /** What the emitted call looks like : null means "the one this field maps to". */
    private record Signature(String itf, String methodName, String fieldParamDesc, String valueParamDesc,
                             boolean withContext) {
    }

    /**
     * One of the three unrolled visitors : per field, the isSet mask test, then the isNull one, then
     * visitXxx with the boxed value or with null. The call is emitted once per branch on purpose — both
     * branch targets are then reached with an empty stack, so every frame is F_SAME and the ClassWriter
     * can keep its flags at 0 rather than paying for COMPUTE_FRAMES.
     */
    private static void generateUnrolledVisitor(ClassWriter classWriter, int id, Field[] fields, boolean is32Bit,
                                                FieldVisitorToVisitName visitor, String name, String descriptor,
                                                String genericSignature, Signature signature) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, name, descriptor,
                genericSignature, new String[]{"java/lang/Exception"});
        methodVisitor.visitCode();
        for (Field field : fields) {
            Label skip = new Label();
            Label nullValue = new Label();
            jumpOnMaskBit(methodVisitor, id, "isSet", field.getIndex(), is32Bit, IFEQ, skip);
            jumpOnMaskBit(methodVisitor, id, "isNull", field.getIndex(), is32Bit, IFNE, nullValue);
            emitVisit(methodVisitor, id, field, visitor, signature, false);
            methodVisitor.visitJumpInsn(GOTO, skip);
            methodVisitor.visitLabel(nullValue);
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            emitVisit(methodVisitor, id, field, visitor, signature, true);
            methodVisitor.visitLabel(skip);
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        }
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitInsn(ARETURN);
        // 4 : visitor + field + a long/double field being boxed, and the long mask test needs as much
        methodVisitor.visitMaxs(4, signature.withContext() ? 3 : 2);
        methodVisitor.visitEnd();
    }

    /** accept(FieldValueVisitor), accept(FieldValueVisitorWithContext, CTX) and apply(FieldValues.Functor). */
    private static void generateUnrolledVisitors(ClassWriter classWriter, int id, Field[] fields, boolean is32Bit,
                                                 FieldVisitorToVisitName visitor) {
        generateUnrolledVisitor(classWriter, id, fields, is32Bit, visitor, "accept",
                "(L" + FIELDS + "FieldValueVisitor;)L" + FIELDS + "FieldValueVisitor;",
                "<T::L" + FIELDS + "FieldValueVisitor;>(TT;)TT;",
                new Signature(FIELDS + "FieldValueVisitor", null, null, null, false));

        generateUnrolledVisitor(classWriter, id, fields, is32Bit, visitor, "accept",
                "(L" + FIELDS + "FieldValueVisitorWithContext;Ljava/lang/Object;)L" + FIELDS + "FieldValueVisitorWithContext;",
                "<CTX:Ljava/lang/Object;T::L" + FIELDS + "FieldValueVisitorWithContext<TCTX;>;>(TT;TCTX;)TT;",
                new Signature(FIELDS + "FieldValueVisitorWithContext", null, null, null, true));

        generateUnrolledVisitor(classWriter, id, fields, is32Bit, visitor, "apply",
                "(Lorg/globsframework/core/model/FieldValues$Functor;)Lorg/globsframework/core/model/FieldValues$Functor;",
                "<T::Lorg/globsframework/core/model/FieldValues$Functor;>(TT;)TT;",
                new Signature("org/globsframework/core/model/FieldValues$Functor", "process",
                        "L" + FIELDS + "Field;", "Ljava/lang/Object;", false));
    }

    public static byte[] generateGlob(int id, GlobType globType) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;

        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, GenerateSetNullVisitor.getGlobName(id), null,
                "org/globsframework/model/generator/primitive/AbstractGeneratedGlob" + (globType.getFieldCount() <= 32 ? "32" : "64"), null);

        Field[] fields = globType.getFields();

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();
        {
            for (Field field : fields) {
                // public, not private : the generated accessor classes GETFIELD/PUTFIELD them directly
                fieldVisitor = classWriter.visitField(ACC_PUBLIC, getFieldName(field), field.safeAccept(visitor.withWithNativeType()).name, null, null);
                fieldVisitor.visitEnd();
            }
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "org/globsframework/model/generator/primitive/AbstractGeneratedGlob" + (globType.getFieldCount() <= 32 ? "32" : "64"), "<init>", "()V", false);

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        generateUnrolledVisitors(classWriter, id, fields, globType.getFieldCount() <= 32, visitor);
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
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id), "setSetAt", "(I)V", false);
                methodVisitor.visitVarInsn(ALOAD, 2);

                Label label0 = new Label();
                methodVisitor.visitJumpInsn(IFNONNULL, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitVarInsn(ILOAD, 3);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id), "forceNull", "(Lorg/globsframework/core/metamodel/fields/Field;I)V", false);
                methodVisitor.visitJumpInsn(GOTO, labelReturn);

                methodVisitor.visitLabel(label0);
                methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{Opcodes.INTEGER}, 0, null);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 3);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id), "setNotNull", "(I)V", false);
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
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id),
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
            methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "forceNull", "(Lorg/globsframework/core/metamodel/fields/Field;I)V", null, null);
            methodVisitor.visitCode();
            Label returnLabel = new Label();
            if (fields.length != 0) {
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id), "setNull", "(I)V", false);
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
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id), "throwError", "(Lorg/globsframework/core/metamodel/fields/Field;)V", false);


            if (fields.length != 0) {
                methodVisitor.visitLabel(returnLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(fields.length == 0 ? 2 : 3, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getType", "()Lorg/globsframework/core/metamodel/GlobType;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitFieldInsn(GETSTATIC, getGlobFactoryName(id), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
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
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id), "isNull", "(I)Z", false);
                Label label0 = new Label();
                methodVisitor.visitJumpInsn(IFNE, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ILOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id), "isSetAt", "(I)Z", false);
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
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, GenerateSetNullVisitor.getGlobName(id), "throwError", "(Lorg/globsframework/core/metamodel/fields/Field;)V", false);
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

        classWriter.visit(V17, ACC_PUBLIC | ACC_SUPER, getGlobFactoryName(id),
                null, "org/globsframework/model/generator/AbstractGeneratedGlobFactory", null);

        {
            fieldVisitor = classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "TYPE",
                    "Lorg/globsframework/core/metamodel/GlobType;", null, null);
            fieldVisitor.visitEnd();
        }

        // the per-field constants did not exist on this flavour : the three accept() need them
        AsmFactoryGenerator.generateFieldConstants(classWriter, globType.getFields());
        AsmFactoryGenerator.generateAccepts(classWriter, getGlobFactoryName(id), globType.getFields());

        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETSTATIC, getGlobFactoryName(id), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
            methodVisitor.visitLdcInsn(id);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generator/primitive/AsmGlobPrimitiveGenerator",
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
            methodVisitor.visitTypeInsn(NEW, GenerateSetNullVisitor.getGlobName(id));
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, GenerateSetNullVisitor.getGlobName(id), "<init>", "()V", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitLdcInsn(id);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "org/globsframework/model/generator/primitive/AsmGlobPrimitiveGenerator",
                    "getType", "(I)Lorg/globsframework/core/metamodel/GlobType;", false);
            methodVisitor.visitFieldInsn(PUTSTATIC, getGlobFactoryName(id), "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");

            AsmFactoryGenerator.generateFieldConstantsInit(methodVisitor, getGlobFactoryName(id), globType.getFields());

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 0);
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

    private static class FieldVisitorToVisitName implements org.globsframework.core.metamodel.fields.FieldVisitor {
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

        public void visitBytes(BytesField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitBytes";
                case fieldType -> "BytesField";
                case outputTypeSimple, nativeType, outputType -> "[B";
                case getAccessor -> "AbstractGlobGetBytesAccessor";
                case setAccessor -> "AbstractGlobSetBytesAccessor";
            };
        }

        public void visitGlob(GlobField<?> field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitGlob";
                case fieldType -> "GlobField";
                case outputTypeSimple -> "org/globsframework/core/model/Glob";
                case nativeType, outputType -> "Lorg/globsframework/core/model/Glob;";
                case getAccessor -> "AbstractGlobGetGlobAccessor";
                case setAccessor -> "AbstractGlobSetGlobAccessor";
            };
        }

        public void visitGlobArray(GlobArrayField<?> field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitGlobArray";
                case fieldType -> "GlobArrayField";
                case outputTypeSimple, nativeType, outputType -> "[Lorg/globsframework/core/model/Glob;";
                case getAccessor -> "AbstractGlobGetGlobArrayAccessor";
                case setAccessor -> "AbstractGlobSetGlobArrayAccessor";
            };
        }

        public void visitUnionGlob(GlobUnionField field) {
            isArray = false;
            name = switch (characteristic) {
                case visitor -> "visitUnionGlob";
                case fieldType -> "GlobUnionField";
                case outputTypeSimple -> "org/globsframework/core/model/Glob";
                case nativeType, outputType -> "Lorg/globsframework/core/model/Glob;";
                case getAccessor -> "AbstractGlobGetGlobUnionAccessor";
                case setAccessor -> "AbstractGlobSetGlobUnionAccessor";
            };
        }

        public void visitUnionGlobArray(GlobArrayUnionField field) {
            isArray = true;
            name = switch (characteristic) {
                case visitor -> "visitUnionGlobArray";
                case fieldType -> "GlobArrayUnionField";
                case outputTypeSimple, nativeType, outputType -> "[Lorg/globsframework/core/model/Glob;";
                case getAccessor -> "AbstractGlobGetGlobUnionArrayAccessor";
                case setAccessor -> "AbstractGlobSetGlobUnionArrayAccessor";
            };
        }
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

        public void visitInteger(IntegerField field) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            methodVisitor.visitFieldInsn(PUTFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), "I");
        }

        public void visitLong(LongField field) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Long");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            methodVisitor.visitFieldInsn(PUTFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), "J");
        }

        public void visitDouble(DoubleField field) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Double");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            methodVisitor.visitFieldInsn(PUTFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), "D");
        }

        public void visitBoolean(BooleanField field) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            methodVisitor.visitFieldInsn(PUTFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), "Z");
        }

        public void notManaged(Field field) {
            methodVisitor.visitTypeInsn(CHECKCAST, field.safeAccept(visitor.withSimpleUserType()).name);
            methodVisitor.visitFieldInsn(PUTFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), field.safeAccept(visitor.withUserType()).name);
        }
    }

    private static class GenerateGetVisitor extends org.globsframework.core.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;

        public GenerateGetVisitor(MethodVisitor methodVisitor, int id) {
            this.methodVisitor = methodVisitor;
            this.id = id;
        }

        public void visitInteger(IntegerField field) {
            methodVisitor.visitFieldInsn(GETFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), "I");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        }

        public void visitDouble(DoubleField field) {
            methodVisitor.visitFieldInsn(GETFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), "D");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        }

        public void visitLong(LongField field) {
            methodVisitor.visitFieldInsn(GETFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), "J");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        }

        public void visitBoolean(BooleanField field) {
            methodVisitor.visitFieldInsn(GETFIELD, GenerateSetNullVisitor.getGlobName(id), getFieldName(field), "Z");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        }

        public void notManaged(Field field) {
            methodVisitor.visitFieldInsn(GETFIELD, GenerateSetNullVisitor.getGlobName(id),
                    getFieldName(field), field.safeAccept(new FieldVisitorToVisitName().withUserType()).name);
        }
    }

    private static class GenerateSetNullVisitor extends org.globsframework.core.metamodel.fields.FieldVisitor.AbstractFieldVisitor {
        private final MethodVisitor methodVisitor;
        private final int id;

        public GenerateSetNullVisitor(MethodVisitor methodVisitor, int id) {
            this.methodVisitor = methodVisitor;
            this.id = id;
        }

        public void visitInteger(IntegerField field) {
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, getGlobName(id), getFieldName(field), "I");
        }

        public void visitDouble(DoubleField field) {
            methodVisitor.visitInsn(DCONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, getGlobName(id), getFieldName(field), "D");
        }

        public void visitLong(LongField field) {
            methodVisitor.visitInsn(LCONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, getGlobName(id), getFieldName(field), "J");
        }

        public void visitBoolean(BooleanField field) {
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitFieldInsn(PUTFIELD, getGlobName(id), getFieldName(field), "Z");
        }

        public void notManaged(Field field) {
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitFieldInsn(PUTFIELD, getGlobName(id),
                    getFieldName(field), field.safeAccept(new FieldVisitorToVisitName().withUserType()).name);
        }

        private static String getGlobName(int id) {
            return "org/globsframework/model/generated/primitive/GeneratedGlob_" + id;
        }
    }
}
