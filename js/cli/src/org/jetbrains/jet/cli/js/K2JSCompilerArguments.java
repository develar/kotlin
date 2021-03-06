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

package org.jetbrains.jet.cli.js;

import com.sampullara.cli.Argument;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.cli.common.CompilerArguments;

public class K2JSCompilerArguments extends CompilerArguments {
    @Argument(value = "output", description = "Output file path")
    public String outputFile;

    @Argument(value = "libraryFiles", description = "Path to zipped lib sources or kotlin files")
    public String[] libraryFiles;

    @Argument(value = "sourceFiles", description = "Source files (dir or file)")
    public String[] sourceFiles;

    @Argument(value = "target", description = "Generate js files for specific ECMA version (3 or 5, default ECMA 3)")
    public String target;

    @Argument(value = "tags", description = "Demarcate each compilation message (error, warning, etc) with an open and close tag")
    public boolean tags;

    @Argument(value = "verbose", description = "Enable verbose logging output")
    public boolean verbose;

    @Argument(value = "version", description = "Display compiler version")
    public boolean version;

    @Nullable
    @Argument(value = "main", description = "Whether a main function should be called; either 'call' or 'noCall', default 'call' (main function will be auto detected)")
    public String main;

    @Argument(value = "sourcemap", description = "Generate SourceMap")
    public boolean sourcemap;

    @Argument(value = "help", alias = "h", description = "Show help")
    public boolean help;

    @Override
    public boolean isHelp() {
        return help;
    }

    @Override
    public boolean isTags() {
        return tags;
    }

    @Override
    public boolean isVersion() {
        return version;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public String getSrc() {
        throw new IllegalStateException();
    }
}
