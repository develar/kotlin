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

package org.jetbrains.k2js;

import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.k2js.config.Config;
import org.jetbrains.k2js.config.EcmaVersion;
import org.jetbrains.k2js.facade.K2JSTranslator;
import org.jetbrains.k2js.facade.MainCallParameters;
import org.jetbrains.kotlin.compiler.CompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.JsCompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.compiler.SubCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ToJsSubCompiler extends SubCompiler {
    @Override
    public void compile(CompilerConfiguration configuration, ModuleInfo moduleInfo, List<JetFile> files) throws IOException {
        MainCallParameters mainCallParameters = createMainCallParameters(configuration.get(JsCompilerConfigurationKeys.MAIN));

        EcmaVersion ecmaVersion = EcmaVersion.fromString(configuration.get(JsCompilerConfigurationKeys.TARGET));

        String outputFile = configuration.get(JsCompilerConfigurationKeys.OUTPUT_FILE);
        if (outputFile == null) {
            outputFile = configuration.getNotNull(CompilerConfigurationKeys.OUTPUT_ROOT).getPath() + File.separatorChar + moduleInfo.getName() + ".js";
        }
        K2JSTranslator.translateAndSaveToFile(mainCallParameters, files, outputFile,
                                              new Config(moduleInfo, ecmaVersion,
                                                         configuration.get(JsCompilerConfigurationKeys.SOURCEMAP, false)),
                                              moduleInfo.getBindingContext());
    }


    private static MainCallParameters createMainCallParameters(String main) {
        return "noCall".equals(main) ? MainCallParameters.noCall() : MainCallParameters.mainWithoutArguments();
    }
}
