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

package org.jetbrains.jet.lang.resolve.lazy;

import org.jetbrains.jet.test.generator.TestDataSource;
import org.jetbrains.jet.test.generator.TestGenerator;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author abreslav
 */
public class LazyResolveNamespaceComparingTestGenerator {
    public static void main(String[] args) throws IOException {
        String testDataFileExtension = "kt";
        new TestGenerator(
            "compiler/tests/",
            testDataFileExtension,
            "org.jetbrains.jet.lang.resolve.lazy",
            "LazyResolveNamespaceComparingTestGenerated",
            "org.jetbrains.jet.lang.resolve.lazy",
            "AbstractLazyResolveNamespaceComparingTest",
            Arrays.asList(
                new TestDataSource(new File("compiler/testData/readKotlinBinaryClass"),
                                   true,
                                   TestGenerator.filterFilesByExtension(testDataFileExtension),
                                   "doTestSinglePackage"),
                new TestDataSource(new File("compiler/testData/readJavaBinaryClass"),
                                   true,
                                   TestGenerator.filterFilesByExtension(testDataFileExtension),
                                   "doTestSinglePackage"),
                new TestDataSource(new File("compiler/testData/lazyResolve"),
                                   true,
                                   TestGenerator.filterFilesByExtension(testDataFileExtension),
                                   "doTest")
            ),
            "LazyResolveNamespaceComparingTestGenerator"
        ).generateAndSave();
    }

    private LazyResolveNamespaceComparingTestGenerator() {}
}
