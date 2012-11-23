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

package org.jetbrains.jet.plugin.compiler;

import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.jet.plugin.JetFileType;

import java.util.Collections;

import static org.jetbrains.jet.compiler.runner.CompilerRunnerConstants.INTERNAL_ERROR_PREFIX;
import static org.jetbrains.jet.compiler.runner.CompilerRunnerConstants.KOTLIN_COMPILER_NAME;

/**
 * @author yole
 */
public class JetCompilerManager implements StartupActivity, DumbAware {
    private static final Logger LOG = Logger.getInstance(JetCompilerManager.class);

    // Comes from external make
    private static final String PREFIX_WITH_COMPILER_NAME = KOTLIN_COMPILER_NAME + ": " + INTERNAL_ERROR_PREFIX;

    @Override
    public void runActivity(Project project) {
        CompilerManager manager = CompilerManager.getInstance(project);
        manager.addTranslatingCompiler(new JetCompiler(),
                                       Collections.<FileType>singleton(JetFileType.INSTANCE),
                                       Collections.singleton(StdFileTypes.CLASS));
        manager.addTranslatingCompiler(new K2JSCompiler(),
                                       Collections.<FileType>singleton(JetFileType.INSTANCE),
                                       Collections.<FileType>singleton(StdFileTypes.JS));
        manager.addCompilableFileType(JetFileType.INSTANCE);

        manager.addCompilationStatusListener(new CompilationStatusListener() {
            @Override
            public void compilationFinished(
                    boolean aborted, int errors, int warnings, CompileContext compileContext
            ) {
                for (CompilerMessage error : compileContext.getMessages(CompilerMessageCategory.ERROR)) {
                    String message = error.getMessage();
                    if (message.startsWith(INTERNAL_ERROR_PREFIX) || message.startsWith(PREFIX_WITH_COMPILER_NAME)) {
                        LOG.error(message);
                    }
                }
            }

            @Override
            public void fileGenerated(String outputRoot, String relativePath) {
            }
        }, project);
    }
}