package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.io.IOException;
import java.util.Collections;

public class JsBuilder extends TargetBuilder<BuildRootDescriptor, JsBuildTarget> {
    public static final String NAME = "K2Js Compiler";

    public JsBuilder() {
        super(Collections.singletonList(JsBuildTargetType.INSTANCE));
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return NAME;
    }

    @Override
    public void build(
            @NotNull JsBuildTarget target,
            @NotNull DirtyFilesHolder<BuildRootDescriptor, JsBuildTarget> holder,
            @NotNull BuildOutputConsumer outputConsumer,
            @NotNull CompileContext context
    ) throws ProjectBuildException, IOException {
        //throw  new IOException("adas");
        // todo
    }
}
