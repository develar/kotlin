/*
 * Copyright 2010-2012 JetBrains s.r.o.
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

package org.jetbrains.jet.plugin.project;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.plugin.versions.KotlinLibrariesNotificationProvider;
import org.jetbrains.k2js.config.EcmaVersion;


@State(name = "K2JSModule", storages = @Storage(file = "$MODULE_FILE$"))
public final class KotlinJsBuildConfigurationManager implements PersistentStateComponent<KotlinJsBuildConfigurationManager> {
    @NotNull
    public static KotlinJsBuildConfigurationManager getInstance(@NotNull Module module) {
        return ModuleServiceManager.getService(module, KotlinJsBuildConfigurationManager.class);
    }

    @NotNull
    private EcmaVersion ecmaVersion = EcmaVersion.defaultVersion();

    private boolean sourcemap;

    public static VirtualFile getLibLocation(Project project) {
        final AccessToken token = ReadAction.start();
        try {
            return KotlinLibrariesNotificationProvider.findLibraryFile(project, false);
        }
        finally {
            token.finish();
        }
    }

    @NotNull
    public EcmaVersion getEcmaVersion() {
        return ecmaVersion;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setEcmaVersion(@NotNull EcmaVersion ecmaVersion) {
        this.ecmaVersion = ecmaVersion;
    }

    public boolean isSourcemap() {
        return sourcemap;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSourcemap(boolean sourcemap) {
        this.sourcemap = sourcemap;
    }

    @Override
    public KotlinJsBuildConfigurationManager getState() {
        return this;
    }

    @Override
    public void loadState(KotlinJsBuildConfigurationManager state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}