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

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jet.jps.model.JpsJsCompilerOutputPackagingElement;
import org.jetbrains.jet.jps.model.JpsJsExtensionService;
import org.jetbrains.jet.jps.model.JsExternalizationConstants;
import org.jetbrains.jet.utils.PathUtil;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderTestCase;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;

import static com.intellij.util.io.TestFileSystemBuilder.fs;
import static org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root;

public class KotlinBuilderTest extends ArtifactBuilderTestCase {
    private static final String SHARED_TEST_SOURCE_FILES_PATH = AbstractKotlinJpsBuildTestCase.TEST_DATA_PATH + "shared";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("kotlin.jps.tests", "true");
    }

    @Override
    public void tearDown() throws Exception {
        System.clearProperty("kotlin.jps.tests");
        super.tearDown();
    }

    private static void copyToSource(String filename, String sourceRoot) throws IOException {
        FileUtil.copy(new File(SHARED_TEST_SOURCE_FILES_PATH + "/" + filename), new File(sourceRoot, filename));
    }

    public void testSingleModule() throws IOException {
        String sourceRoot = getAbsolutePath("src");
        copyToSource("a.kt", sourceRoot);
        String moduleName = "m";
        JpsArtifact artifact = createModuleAndArtifact(moduleName, sourceRoot);
        rebuildAll();
        assertOutput(artifact, fs().file(moduleName + ".js"));
    }

    private JpsArtifact createModuleAndArtifact(String moduleName, String srcRoot) {
        return addArtifact(moduleName, root().element(gwtOutput(createModule(moduleName, srcRoot))));
    }

    private static JpsJsCompilerOutputPackagingElement gwtOutput(JpsModule m) {
        return new JpsJsCompilerOutputPackagingElement(m.createReference());
    }

    private JpsModule createModule(String moduleName, String srcRoot) {
        JpsModule module = addModule(moduleName, srcRoot);
        addCompilerLibrary(module);
        JpsJsExtensionService.getInstance().setExtension(module);
        return module;
    }

    private static void addCompilerLibrary(JpsModule module) {
        JpsLibrary library = module.addModuleLibrary(JsExternalizationConstants.JS_LIBRARY_NAME, JpsJavaLibraryType.INSTANCE);
        library.addRoot(PathUtil.getKotlinPathsForDistDirectory().getRuntimePath(false), JpsOrderRootType.SOURCES);
        addModuleLibrary(module, library);
    }

    private static void addModuleLibrary(JpsModule module, JpsLibrary library) {
        JpsLibraryDependency libraryDependency = module.getDependenciesList().addLibraryDependency(library);
        JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(libraryDependency);
    }
}
