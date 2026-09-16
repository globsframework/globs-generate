package org.globsframework.model.generator;

import org.globsframework.core.model.caller.ToGlobCallerService;
import org.globsframework.core.model.caller.ToGlobCallerFactory;

/**
 * What {@code -Dglobs.caller.toGlob=org.globsframework.model.generator.AsmCallerWriteGeneratorService} installs :
 * from then on {@code ToGlobCallerFactory.get()} answers a generating factory instead of the looped
 * {@code LoopToGlobCallerFactory}, and every parser written against core gets its switch generated.
 * <p>
 * Independent of {@code globs.builder} and of {@code globs.caller.fromGlob} : the to-Glob side never reads the layout of
 * a Glob, so this works whoever built the Glob the functions write into. One instance serves everything —
 * there is no GlobType to be "not mine" about, hence no null answer here.
 * <p>
 * {@code -Dglobs.caller.toGlob.chunk=<n>} sets how many entries of an unrolled caller go into one emitted
 * method — see {@link AsmCallerWriteGenerator} for what it buys and when.
 */
public class AsmCallerWriteGeneratorService implements ToGlobCallerService {

    public ToGlobCallerFactory factory() {
        // re-read per call rather than cached : a test that changes the chunk and resets the service gets the
        // generator it asked for, and a generator is stateless anyway
        return AsmCallerWriteGenerator.fromProperty();
    }
}
