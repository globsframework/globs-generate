package org.globsframework.model.generator;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.generate.read.GenerateCaller;
import org.globsframework.core.model.generate.read.GenerateCallerService;

/**
 * What {@code -Dglobs.caller=org.globsframework.model.generator.AsmCallerGeneratorService} installs : from
 * then on {@code GenerateCaller.callerFor} answers a generated caller for the types core builds itself,
 * instead of the looped {@code DefaultFunctionCaller}.
 * <p>
 * It is only about the <em>traversal</em>. Nothing is generated for the type — no Glob class, no accessors —
 * so this is the setting for an application that measured {@code -Dglobs.builder=...} to be a loss on its own
 * code (a generated {@code doGet} is a tableswitch over every field, which stops inlining past ~20 fields
 * where core's array access never does) but still wants its codecs to walk a Glob without a megamorphic call
 * per field. The two properties are independent : with both set, a generated type's own factory answers
 * first and this service is only asked about the types that fell back.
 * <p>
 * Answers null — "not mine", not an error — for a type whose factory does not build an AbstractDefaultGlob.
 */
public class AsmCallerGeneratorService implements GenerateCallerService {

    public GenerateCaller getGenerateCaller(GlobType type) {
        return AsmCallerGenerator.forDefaultGlob(type);
    }
}
