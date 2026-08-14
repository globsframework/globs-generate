package org.globsframework.model.generator;

import org.globsframework.core.model.generate.write.GenerateCallerWriteService;
import org.globsframework.core.model.generate.write.GeneratedFunctionCallerWrite;

/**
 * What {@code -Dglobs.callerWrite=org.globsframework.model.generator.AsmCallerWriteGeneratorService} installs :
 * from then on {@code GeneratedFunctionCallerWrite.get()} answers a generating factory instead of the looped
 * {@code DefaultFunctionCallerWrite}, and every parser written against core gets its switch generated.
 * <p>
 * Independent of {@code globs.builder} and of {@code globs.caller} : the write side never reads the layout of
 * a Glob, so this works whoever built the Glob the functions write into. One instance serves everything —
 * there is no GlobType to be "not mine" about, hence no null answer here.
 */
public class AsmCallerWriteGeneratorService implements GenerateCallerWriteService {

    public GeneratedFunctionCallerWrite getGenerateCallerWrite() {
        return AsmCallerWriteGenerator.INSTANCE;
    }
}
