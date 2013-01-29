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

package org.jetbrains.k2js.test.config;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.k2js.config.Config;
import org.jetbrains.k2js.config.EcmaVersion;

import java.util.Arrays;
import java.util.List;

public class TestConfig extends Config {
    @NotNull
    public static final String TEST_MODULE_NAME = "JS_TESTS";
    /**
     * The file names in the standard library to compile
     */
    @NotNull
    public static final List<String> STDLIB_FILE_NAMES = Arrays.asList(
            "kotlin/Preconditions.kt",
            "kotlin/Iterators.kt",
            "kotlin/JUtil.kt",
            "kotlin/Collections.kt",
            "kotlin/Maps.kt",
            "kotlin/Iterables.kt",
            "kotlin/IterablesLazy.kt",
            "kotlin/IterablesSpecial.kt",
            "generated/ArraysFromIterables.kt",
            "generated/ArraysFromIterablesLazy.kt",
            "generated/ArraysFromCollections.kt",
            "generated/IteratorsFromIterables.kt",
            "kotlin/support/AbstractIterator.kt",
            "kotlin/Standard.kt",
            "kotlin/Strings.kt",
            "kotlin/dom/Dom.kt",
            "kotlin/test/Test.kt"
    );
    /**
     * The location of the stdlib sources
     */
    public static final String STDLIB_LOCATION = "libraries/stdlib/src";
    @NotNull
    public static final List<String> LIB_FILE_NAMES = Lists.newArrayList();
    /**
     * the library files which depend on the STDLIB files to be able to compile
     */
    @NotNull
    public static final List<String> LIB_FILE_NAMES_DEPENDENT_ON_STDLIB = Arrays.asList(
            "stdlib/TuplesCode.kt",
            "src/core/stringsCode.kt",
            "stdlib/domCode.kt",
            "stdlib/jutilCode.kt",
            "stdlib/JUMapsCode.kt",
            "stdlib/testCode.kt"
    );
    public static final String LIBRARIES_LOCATION = "js/js.libraries";
    @NotNull
    public static final List<String> LIB_FILES_WITH_CODE = Arrays.asList(
            "stdlib/TuplesCode.kt",
            "src/core/javautilCode.kt"
    );
    @NotNull
    public static final List<String> LIB_FILES_WITH_DECLARATIONS = Arrays.asList(
            "src/annotations.kt",
            "src/core.kt",
            "generated/ecmaScript5.kt",
            "generated/dom.kt",
            "src/core/javaio.kt",
            "src/javalang.kt",
            "src/core/javautil.kt",
            "src/json.kt",
            "src/core/kotlin.kt",
            "src/core/math.kt",
            "src/string.kt",
            "generated/html5.kt",
            "src/jquery/common.kt",
            "src/jquery/ui.kt",
            "src/junit/core.kt",
            "src/qunit/core.kt",
            "stdlib/browser.kt",
            "src/requirejs.kt"
    );

    @NotNull
    public static TestConfigFactory FACTORY = new TestConfigFactory() {
        @Override
        public TestConfig create(@NotNull ModuleInfo moduleConfiguration, @NotNull EcmaVersion version) {
            return new TestConfig(moduleConfiguration, version);
        }
    };

    public TestConfig(@NotNull ModuleInfo module, @NotNull EcmaVersion version) {
        super(module, version);
    }

    static {
        LIB_FILE_NAMES.addAll(LIB_FILES_WITH_DECLARATIONS);
        LIB_FILE_NAMES.addAll(LIB_FILES_WITH_CODE);
    }
}
