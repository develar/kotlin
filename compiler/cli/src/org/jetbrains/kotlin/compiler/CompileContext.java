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

package org.jetbrains.kotlin.compiler;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;

public class CompileContext extends CompilerContextBase<CoreProjectEnvironment> {
    public CompileContext(@NotNull Disposable parentDisposable) {
        super(new CoreApplicationEnvironment(parentDisposable));

        projectEnvironment = new CoreProjectEnvironment(parentDisposable, applicationEnvironment);
        registerFileTypes();
        KotlinBuiltIns.initialize(getProject());
    }

    @Override
    protected void registerFileTypes() {
        super.registerFileTypes();

        applicationEnvironment.registerFileType(ArchiveFileType.INSTANCE, "zip");
    }
}
