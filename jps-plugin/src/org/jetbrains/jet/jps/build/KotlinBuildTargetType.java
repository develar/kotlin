/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.jps.build;

import com.intellij.util.PairConsumer;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.jps.model.JpsKotlinCompilerOutputPackagingElement;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

import java.util.Map;

public abstract class KotlinBuildTargetType extends BuildTargetType<KotlinBuildTarget> {
    protected KotlinBuildTargetType(String typeId) {
        super(typeId);
    }

    public abstract String getLanguageName();

    public abstract String getSubCompilerClassName();

    protected static void collectTargets(
            JpsModel model,
            KotlinBuildTargetType targetType,
            PairConsumer<JpsModule, KotlinBuildTarget> consumer
    ) {
        THashSet<JpsModuleReference> visited = new THashSet<JpsModuleReference>(new TObjectHashingStrategy<JpsModuleReference>() {
            @Override
            public int computeHashCode(JpsModuleReference reference) {
                return reference.getModuleName().hashCode();
            }

            @Override
            public boolean equals(
                    JpsModuleReference reference, JpsModuleReference reference2
            ) {
                return reference.getModuleName().equals(reference2.getModuleName());
            }
        });

        for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(model.getProject())) {
            // todo if artifact created in tests, should we set build on make?
            //if (!artifact.isBuildOnMake()) {
            //    continue;
            //}

            for (JpsPackagingElement element : artifact.getRootElement().getChildren()) {
                if (element instanceof JpsKotlinCompilerOutputPackagingElement) {
                    JpsModuleReference reference = ((JpsKotlinCompilerOutputPackagingElement) element).getModuleReference();
                    if (visited.add(reference)) {
                        JpsModule module = reference.resolve();
                        if (module != null) {
                            consumer.consume(module, new KotlinBuildTarget(module, targetType));
                        }
                    }
                }
            }
        }
    }

    protected static class Loader extends BuildTargetLoader<KotlinBuildTarget> {
        private final Map<String, KotlinBuildTarget> targets;

        public Loader(JpsModel model, KotlinBuildTargetType targetType) {
            targets = new THashMap<String, KotlinBuildTarget>();
            collectTargets(model, targetType, new PairConsumer<JpsModule, KotlinBuildTarget>() {
                @Override
                public void consume(JpsModule module, KotlinBuildTarget target) {
                    targets.put(module.getName(), target);
                }
            });
        }

        @Nullable
        @Override
        public KotlinBuildTarget createTarget(@NotNull String targetId) {
            final KotlinBuildTarget target = targets.get(targetId);
            assert target != null;
            return target;
        }
    }
}
