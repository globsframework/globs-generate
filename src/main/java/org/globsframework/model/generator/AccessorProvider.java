package org.globsframework.model.generator;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;

/**
 * Where {@link AbstractGeneratedGlobFactory} gets its accessors from, one field at a time, in its
 * constructor. The implementation is {@link AsmAccessorGenerator#providerFor} : it needs the generated
 * ClassLoader holding the generated accessor classes, which only the generator has, so it is handed over
 * through the same per-id channel as the GlobType rather than built by the factory itself.
 */
public interface AccessorProvider {

    GlobGetAccessor get(Field field);

    GlobSetAccessor set(Field field);
}
