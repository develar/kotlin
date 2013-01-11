package org.jetbrains.jet.jps.build;

import org.jetbrains.jps.builders.BuildTargetType;

public abstract class KotlinBuildTargetType extends BuildTargetType<KotlinBuildTarget> {
    protected KotlinBuildTargetType(String typeId) {
        super(typeId);
    }

    public abstract String getLanguageName();
}
