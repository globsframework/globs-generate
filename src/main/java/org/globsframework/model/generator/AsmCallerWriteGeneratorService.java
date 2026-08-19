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
 */
public class AsmCallerWriteGeneratorService implements ToGlobCallerService {

    public ToGlobCallerFactory factory() {
        return AsmCallerWriteGenerator.INSTANCE;
    }
}
