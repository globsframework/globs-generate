package org.globsframework.model.generator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The one loader every class this module generates is defined in : Globs, factories, accessors and callers
 * alike.
 * <p>
 * It replaces the anonymous {@code new ClassLoader(parent) {}} each generation used to build for itself. A
 * name is the point — {@link #getName()} answers {@code globs-generated} and the loader is an ordinary named
 * class rather than {@code AsmGlobObjectGenerator$1} — because a class of a user-defined loader is matched
 * against an AOT cache on its loader as well as on its own name and bytes, and an instance conjured per
 * generation has no identity to match. Stable class names (see {@link GeneratedName}) were the other half.
 * <p>
 * <b>Nothing is deduplicated here</b>, and that is not an oversight : a generated factory holds a
 * {@code static final TYPE} bound to one GlobType <em>instance</em>, so two distinct types that happen to
 * have the same name and layout — the same family key — must still get two classes, or the second factory
 * would answer the first type. {@link GeneratedName} is what keeps them apart, by suffixing a key already in
 * use; without it, sharing one loader would be a duplicate class definition instead of two classes.
 * <p>
 * <b>The consequence to know</b> : a generated class now lives as long as this loader, i.e. as long as the
 * process, where a throwaway loader was collectable with the factory or caller it was built for. For Globs
 * that changes little — a GlobType is normally built once and kept — but an application that builds
 * throwaway types, or a codec rebuilt over and over, now accumulates classes in metaspace. The way out, if
 * it ever matters, is a second instance of this loader per disposable domain rather than a return to
 * anonymous ones : the name is what has to be kept.
 */
public final class GeneratedClassLoader extends ClassLoader {
    static {
        // loadClass locks per class name rather than on the loader : the generators are called from
        // DefaultGlobType's constructor, on whatever thread builds a type
        registerAsParallelCapable();
    }

    private static final GeneratedClassLoader SHARED = new GeneratedClassLoader();

    // What to emit for a class that has not been asked for yet. The suppliers close over a GlobType, so an
    // entry is dropped as soon as its class is defined -- and an accessor that will never be loaded (the
    // doGet-based option) must not be registered in the first place.
    private final Map<String, Supplier<byte[]>> emitters = new ConcurrentHashMap<>();

    private GeneratedClassLoader() {
        // the parent is this module's own loader : the emitted code names core interfaces, this module's
        // abstract bases and the other generated classes, and nothing else
        super("globs-generated", GeneratedClassLoader.class.getClassLoader());
    }

    public static GeneratedClassLoader get() {
        return SHARED;
    }

    /**
     * Says what to emit for a class name, without generating anything : the bytes are built when the class
     * is first asked for, which for a Glob may be long after its factory was built.
     *
     * @throws IllegalStateException if that name is already spoken for — two generations that agree on a
     *                               name would emit one class and use it for both, so this is the check that
     *                               the naming scheme is doing its job.
     */
    public void emit(String internalName, Supplier<byte[]> bytes) {
        if (emitters.putIfAbsent(internalName, bytes) != null) {
            throw new IllegalStateException(internalName + " is already waiting to be generated : two "
                                            + "generations agreed on one class name.");
        }
    }

    /** Loads a class this loader was told how to emit. A failure here is a generation bug, not a lookup. */
    public Class<?> load(String internalName) {
        try {
            return loadClass(internalName.replace('/', '.'));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Nothing was registered to generate " + internalName, e);
        }
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String internalName = name.replace('.', '/');
        Supplier<byte[]> bytes = emitters.get(internalName);
        if (bytes == null) {
            return super.findClass(name);
        }
        byte[] b = bytes.get();
        Class<?> generated = defineClass(name, b, 0, b.length);
        // only once it is defined : a failure above should leave the entry for the error to be read twice
        emitters.remove(internalName);
        return generated;
    }
}
