package org.globsframework.model.generator;

import org.globsframework.core.model.caller.CallerShape;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;

import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * The pair of interfaces a caller is emitted over, and everything both generators need to know about it :
 * the two methods core's {@link CallerShape#methodMatching} matched, the descriptor of each, and where each
 * argument sits once it is on a frame.
 * <p>
 * Shared by {@link AsmCallerGenerator} and {@link AsmCallerWriteGenerator} because it is the one thing the
 * two sides really do the same way. What differs is only what each puts in front of the arguments the codec
 * chose : nothing and nothing on the to-Glob side (both methods take the same list), the Glob and the
 * isSet / isNull / value triple on the from-Glob one.
 */
record EmittedShape(Class<?> tClass, Class<?> dClass, Method callerMethod, Method functionMethod,
                    String callerDescriptor, String functionDescriptor, Type[] callerArguments) {

    static EmittedShape of(Class<?> tClass, Class<?> dClass, Method callerMethod, Method functionMethod) {
        String callerDescriptor = Type.getMethodDescriptor(callerMethod);
        return new EmittedShape(tClass, dClass, callerMethod, functionMethod, callerDescriptor,
                Type.getMethodDescriptor(functionMethod), Type.getArgumentTypes(callerDescriptor));
    }

    String itf() {
        return Type.getInternalName(tClass);
    }

    String function() {
        return Type.getInternalName(dClass);
    }

    String functionDesc() {
        return Type.getDescriptor(dClass);
    }

    /** Where caller argument {@code index} starts, the first one being at {@code first} — two slots for a long or a double. */
    int slotOf(int index, int first) {
        int slot = first;
        for (int i = 0; i < index; i++) {
            slot += callerArguments[i].getSize();
        }
        return slot;
    }

    /** The first free slot after the caller's arguments, {@code this} being slot 0. */
    int slotAfterArguments() {
        return slotOf(callerArguments.length, 1);
    }

    /**
     * The caller's arguments from {@code firstArgument} on, each loaded with the opcode of its own type and
     * from its own slot. The from-Glob side starts at 1, having pushed the field's state instead of the Glob.
     */
    void loadArguments(MethodVisitor methodVisitor, int firstArgument, int firstSlot) {
        int slot = slotOf(firstArgument, firstSlot);
        for (int i = firstArgument; i < callerArguments.length; i++) {
            methodVisitor.visitVarInsn(callerArguments[i].getOpcode(ILOAD), slot);
            slot += callerArguments[i].getSize();
        }
    }

    /** {@code fn.d(...)}, whatever the arguments already on the stack are. */
    void invokeFunction(MethodVisitor methodVisitor) {
        methodVisitor.visitMethodInsn(dClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, function(),
                functionMethod.getName(), functionDescriptor, dClass.isInterface());
    }

    /**
     * What the caller's own method declares. The JVM does not check it, but a caller's method may declare a
     * checked exception its functions throw — the emitted class is the interface, so it says the same thing.
     */
    String[] exceptions() {
        Class<?>[] exceptions = callerMethod.getExceptionTypes();
        if (exceptions.length == 0) {
            return null;
        }
        String[] names = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; i++) {
            names[i] = Type.getInternalName(exceptions[i]);
        }
        return names;
    }

    /** Everything about the pair that changes an emitted byte, in front of what the shape is built for. */
    String[] identity(String... rest) {
        String[] identity = new String[rest.length + 6];
        identity[0] = tClass.getName();
        identity[1] = dClass.getName();
        identity[2] = callerMethod.getName();
        identity[3] = functionMethod.getName();
        identity[4] = callerDescriptor;
        identity[5] = functionDescriptor;
        System.arraycopy(rest, 0, identity, 6, rest.length);
        return identity;
    }
}
