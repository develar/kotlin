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

package org.jetbrains.k2js.facade;

import com.google.dart.compiler.backend.js.ast.JsProgram;
import com.google.dart.compiler.util.TextOutputImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.js.compiler.JsSourceGenerationVisitor;
import org.jetbrains.js.compiler.SourceMapBuilder;
import org.jetbrains.js.compiler.sourcemap.SourceMap3Builder;
import org.jetbrains.k2js.config.Config;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.utils.JetFileUtils;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.lang.resolve.XAnalyzerFacade;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.facade.FacadeUtils.parseString;

/**
 * An entry point of translator.
 */
public final class K2JSTranslator {
    public static final String FLUSH_SYSTEM_OUT = "Kotlin.System.flush();\n";
    public static final String GET_SYSTEM_OUT = "Kotlin.System.output();\n";

    @NotNull
    private final Config config;

    public static void translateAndSaveToFile(
            @NotNull MainCallParameters mainCallParameters,
            @NotNull List<JetFile> files,
            @NotNull String outputPath,
            @NotNull Config config,
            @NotNull BindingContext bindingContext,
            @Nullable Consumer<File> outputFileConsumer
    ) throws IOException {
        File outFile = new File(outputPath);
        TextOutputImpl output = new TextOutputImpl();
        SourceMapBuilder sourceMapBuilder = config.isSourcemap() ? new SourceMap3Builder(outFile, output, new SourceMapBuilderConsumer()) : null;

        String programCode = toSource(output, sourceMapBuilder, Translation.generateAst(bindingContext, files, mainCallParameters, config));
        FileUtil.writeToFile(outFile, programCode);
        if (sourceMapBuilder != null) {
            File sourcemapFile = sourceMapBuilder.getOutFile();
            FileUtil.writeToFile(sourcemapFile, sourceMapBuilder.build());
            if (outputFileConsumer != null) {
                outputFileConsumer.consume(sourcemapFile);
            }
        }

        if (outputFileConsumer != null) {
            outputFileConsumer.consume(outFile);
        }
    }

    public K2JSTranslator(@NotNull Config config) {
        this.config = config;
    }

    //NOTE: web demo related method
    @SuppressWarnings("UnusedDeclaration")
    @NotNull
    public String translateStringWithCallToMain(@NotNull String programText, @NotNull String argumentsString) {
        JetFile file = JetFileUtils.createPsiFile("test", programText, config.getProject());
        KotlinBuiltIns.initialize(config.getProject(), KotlinBuiltIns.InitializationMode.SINGLE_THREADED);
        List<JetFile> files = Collections.singletonList(file);
        String programCode = toSource(new TextOutputImpl(), null, Translation
                .generateAst(analyzeFilesAndCheckErrors(files, config), files,
                             MainCallParameters.mainWithArguments(parseString(argumentsString)), config));
        return FLUSH_SYSTEM_OUT + programCode + "\n" + GET_SYSTEM_OUT;
    }

    @NotNull
    private static BindingContext analyzeFilesAndCheckErrors(
            @NotNull List<JetFile> files,
            @NotNull Config config
    ) {
        final ModuleInfo libraryModuleConfiguration = new ModuleInfo(config.getProject());
        // todo fix web demo
        AnalyzeExhaust libraryExhaust = XAnalyzerFacade.analyzeFiles(libraryModuleConfiguration, Collections.<JetFile>emptyList(), false);
        libraryExhaust.throwIfError();

        XAnalyzerFacade.checkForErrors(files);
        return XAnalyzerFacade.analyzeFiles(
                new ModuleInfo(Name.special("<web-demo>"), config.getProject(), libraryModuleConfiguration), files,
                true).getBindingContext();
    }

    private static String toSource(TextOutputImpl output, SourceMapBuilder sourceMapBuilder, JsProgram program) {
        JsSourceGenerationVisitor sourceGenerator = new JsSourceGenerationVisitor(output, sourceMapBuilder);
        program.accept(sourceGenerator);
        return output.toString();
    }
}
