package org.globsframework.model.generator;

import org.globsframework.core.model.GlobFactory;

/**
 * The GlobFactory of a generated type, which can also generate callers for it.
 * <p>
 * This is the entry point for the outside : a module that wants to trade its megamorphic per-field dispatch
 * for generated call sites asks the type for its factory and tests it. Most callers should not do that by
 * hand and should ask {@link GenerateCaller#callerFor}, which falls back to a {@link DefaultFunctionCaller}
 * for a type that has no generated class instead of leaving them with a second code path.
 * <pre>
 * GeneratedFunctionCaller&lt;Out, Void&gt; caller =
 *     type.getGlobFactory() instanceof GlobGenerateFactory generate
 *         ? generate.create(field -&gt; functionFor(field))
 *         : null;   // not generated (globs.builder unset, mode none, or more than 64 fields)
 * </pre>
 */
public interface GlobGenerateFactory extends GlobFactory, GenerateCaller {
}
