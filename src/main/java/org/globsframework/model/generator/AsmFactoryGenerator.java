package org.globsframework.model.generator;

import org.globsframework.core.metamodel.fields.Field;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.util.regex.Pattern;

import static org.objectweb.asm.Opcodes.*;

/**
 * The factory side shared by both flavours : the per-field static Field constants, and the three
 * GlobFactory.accept overloads unrolled.
 * <p>
 * Those three accept() walk the fields of the GlobType, not the values of a Glob, so the unrolled form is
 * just one INVOKEINTERFACE per field on the static constant — no loop, no isSet test, no branch at all.
 */
public class AsmFactoryGenerator {
    private static final Pattern NON_WORD = Pattern.compile("[^\\w]");
    private static final String FIELDS = "org/globsframework/core/metamodel/fields/";

    /** GlobType field names may contain spaces, Java identifiers may not. */
    public static String fieldName(Field field) {
        return NON_WORD.matcher(field.getName()).replaceAll("_");
    }

    private static String fieldTypeOf(Field field, FieldVisitorToVisitName visitor) {
        return field.safeAccept(visitor.withFieldType()).name;
    }

    /** public static final IntegerField myField; ... one per field. */
    public static void generateFieldConstants(ClassWriter classWriter, Field[] fields) {
        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();
        for (Field field : fields) {
            classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, fieldName(field),
                            "L" + FIELDS + fieldTypeOf(field, visitor) + ";", null, null)
                    .visitEnd();
        }
    }

    /** The part of {@code <clinit>} that resolves each constant from TYPE; the caller has already set TYPE. */
    public static void generateFieldConstantsInit(MethodVisitor methodVisitor, String factoryName, Field[] fields) {
        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();
        for (Field field : fields) {
            methodVisitor.visitFieldInsn(GETSTATIC, factoryName, "TYPE", "Lorg/globsframework/core/metamodel/GlobType;");
            methodVisitor.visitLdcInsn(field.getName());
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/globsframework/core/metamodel/GlobType",
                    "findField", "(Ljava/lang/String;)L" + FIELDS + "Field;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, FIELDS + fieldTypeOf(field, visitor));
            methodVisitor.visitFieldInsn(PUTSTATIC, factoryName, fieldName(field),
                    "L" + FIELDS + fieldTypeOf(field, visitor) + ";");
        }
    }

    /**
     * The three GlobFactory.accept overloads : FieldVisitor, FieldVisitorWithContext and
     * FieldVisitorWithTwoContext. They override the default methods of the interface, which loop over
     * getGlobType().getFields() and dispatch through Field.accept.
     */
    public static void generateAccepts(ClassWriter classWriter, String factoryName, Field[] fields) {
        generateAccept(classWriter, factoryName, fields, "FieldVisitor", 0,
                "<T::L" + FIELDS + "FieldVisitor;>(TT;)TT;");
        generateAccept(classWriter, factoryName, fields, "FieldVisitorWithContext", 1,
                "<T::L" + FIELDS + "FieldVisitorWithContext<TC;>;C:Ljava/lang/Object;>(TT;TC;)TT;");
        generateAccept(classWriter, factoryName, fields, "FieldVisitorWithTwoContext", 2,
                "<T::L" + FIELDS + "FieldVisitorWithTwoContext<TC;TD;>;C:Ljava/lang/Object;D:Ljava/lang/Object;>(TT;TC;TD;)TT;");
    }

    private static void generateAccept(ClassWriter classWriter, String factoryName, Field[] fields,
                                       String visitorInterface, int contextCount, String signature) {
        String visitorDesc = "L" + FIELDS + visitorInterface + ";";
        StringBuilder contexts = new StringBuilder();
        for (int i = 0; i < contextCount; i++) {
            contexts.append("Ljava/lang/Object;");
        }

        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "accept",
                "(" + visitorDesc + contexts + ")" + visitorDesc, signature,
                new String[]{"java/lang/Exception"});
        methodVisitor.visitCode();

        FieldVisitorToVisitName visitor = new FieldVisitorToVisitName();
        for (Field field : fields) {
            String fieldType = fieldTypeOf(field, visitor);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitFieldInsn(GETSTATIC, factoryName, fieldName(field), "L" + FIELDS + fieldType + ";");
            for (int i = 0; i < contextCount; i++) {
                methodVisitor.visitVarInsn(ALOAD, 2 + i);
            }
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, FIELDS + visitorInterface,
                    field.safeAccept(visitor.withMethodVisitor()).name,
                    "(L" + FIELDS + fieldType + ";" + contexts + ")V", true);
        }

        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(2 + contextCount, 2 + contextCount);
        methodVisitor.visitEnd();
    }
}
