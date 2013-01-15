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
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.jps.model.JpsKotlinCompilerOutputPackagingElement;
import org.jetbrains.jet.jps.model.JsExternalizationConstants;
import org.jetbrains.jet.utils.PathUtil;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderTestCase;
import org.jetbrains.jps.incremental.artifacts.ModuleBuilder;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;

import static com.intellij.util.io.TestFileSystemBuilder.fs;
import static org.jetbrains.jps.incremental.artifacts.ModuleBuilder.createArtifact;

public class KotlinBuilderTest extends ArtifactBuilderTestCase {
    // todo fix AbstractKotlinJpsBuildTestCase.TEST_DATA_PATH
    private static final String TEST_DATA_PATH = StringUtil.trimEnd(AbstractKotlinJpsBuildTestCase.TEST_DATA_PATH, "/");

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

    public void testSingleModule() throws IOException {
        ModuleBuilder main = createModuleBuilder();
        JpsArtifact artifact = main.copy("a.kt").createArtifact();
        rebuildAll();
        assertOutput(artifact, fs().file(main.getName() + ".js"));
    }

    public void testSeveralIndependentModules() throws IOException {
        ModuleBuilder a = createModuleBuilder().copy("a.kt").artifact();
        ModuleBuilder b = createModuleBuilder().copy("a.kt").artifact();

        rebuildAll();

        final String aOutFilename = a.getName() + ".js";
        assertOutput(a.getArtifact(), fs().file(aOutFilename));
        assertOutput(b.getArtifact(), fs().file(b.getName() + ".js"));

        makeAll().assertUpToDate();

        File aOutFile = new File(a.getArtifact().getOutputPath(), aOutFilename);
        long aOutFileLastModified = aOutFile.lastModified();
        JpsArtifact aSecondArtifact = a.createArtifact();

        makeAll().assertSuccessful();

        a.file("ignoreNotKotlinFile.txt", "dd");

        assertNoKotlinModulesRecompiled();
        assertOutput(aSecondArtifact, fs().file(aOutFilename));
        assertEquals(aOutFileLastModified, aOutFile.lastModified());
    }

    // a
    // b -> a
    // c -> a[exported]
    // d -> c
    public void testDependentModule() throws IOException {
        ModuleBuilder a = createModuleBuilder().copy("a.kt");
        ModuleBuilder b = createModuleBuilder().dependsOn(a).copy("b.kt");

        JpsArtifact artifactAB = createArtifact(a, b);

        rebuildAll();

        ModuleBuilder c = createModuleBuilder().dependsOnAndExports(a).copy("c.kt");
        makeAll().assertSuccessful();
        c.assertCompiled("c.kt");

        JpsArtifact artifactABC = createArtifact(a, b, c);
        makeAll().assertSuccessful();
        assertOutput(artifactAB, fs().file(a.getName() + ".js").file(b.getName() + ".js"));
        assertOutput(artifactABC, fs().file(c.getName() + ".js").file(a.getName() + ".js").file(b.getName() + ".js"));

        ModuleBuilder d = createModuleBuilder().dependsOn(c).copy("d.kt").artifact();
        makeAll().assertSuccessful();
        d.assertCompiled("d.kt");
        // todo we must test that we cannot use symbols from transitive unexported module dependency
        //copyProjectDir();
    }

    private void copyProjectDir() throws IOException {
        final File dir = new File("/Users/develar/test");
        FileUtil.delete(dir);
        FileUtil.copyDir(new File(getAbsolutePath(".")), dir);
    }

    //public void testSeveralModuleDependencies() throws IOException {
    //    ModuleBuilder chromeExtApi = createModuleBuilder().file("chrome-ext-api.kt", "package org.jetbrains.chromium.debug\n" +
    //                                                                                 "\n" +
    //                                                                                 "public native fun dto<T>(vararg  p:Any?):T = noImpl");
    //    //ModuleBuilder c = createModuleBuilder().dependsOn(chromeExtApi).copy("c.kt").artifact();
    //
    //    ModuleBuilder chromeExt = createModuleBuilder().dependsOn(chromeExtApi).file("chrome-ext.kt", "package com.jetbrains.browserConnection.chrome\n" +
    //                                                                                                  "\n" +
    //                                                                                                  "import org.jetbrains.chromium.debug.dto\n" +
    //                                                                                                  "\n" +
    //                                                                                                  "fun f() = dto<String>(\"dd\")");
    //    JpsArtifact artifact = chromeExt.createArtifact();
    //    new LayoutElementTestUtil.LayoutElementCreator(artifact.getRootElement(), null).element(((MyModuleBuilder) chromeExt).createPackagingElement(chromeExtApi.get()));
    //    rebuildAll();
    //    makeAll().assertSuccessful();
    //    copyProjectDir();
    //}

    @NotNull
    @Override
    protected String getTestDataRootPath() {
        return TEST_DATA_PATH;
    }

    @Override
    protected void loadProject(String projectPath) {
        super.loadProject(projectPath.charAt(0) == '/' ? FileUtil.getRelativePath(new File(getAbsolutePath(".")), new File(projectPath)) : projectPath);
    }

    //public void testA() {
    //    loadProject("/Users/develar/Documents/test-idea-kotlin-project");
    //    //for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(myProject)) {
    //    //    if (artifact.getName().equals("Chrome extension")) {
    //    //        doBuild(CompileScopeTestBuilder.make().artifact(artifact)).assertSuccessful();
    //    //        break;
    //    //    }
    //    //}
    //    rebuildAll();
    //    for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(myProject)) {
    //        artifact.setOutputPath(null);
    //    }
    //}

    private void assertNoKotlinModulesRecompiled() {
        assertCompiled(JsBuildTargetType.BUILDER_NAME);
    }

    private ModuleBuilder createModuleBuilder() {
        return new MyModuleBuilder(this);
    }

    private static class MyModuleBuilder extends ModuleBuilder {
        public MyModuleBuilder(KotlinBuilderTest testCase) {
            super(JsBuildTargetType.BUILDER_NAME, testCase, AbstractKotlinJpsBuildTestCase.TEST_DATA_PATH + "shared");
        }

        @Override
        protected void moduleCreated(JpsModule module) {
            JpsLibrary library = module.addModuleLibrary(JsExternalizationConstants.JS_LIBRARY_NAME, JpsJavaLibraryType.INSTANCE);
            library.addRoot(PathUtil.getKotlinPathsForDistDirectory().getRuntimePath(false), JpsOrderRootType.SOURCES);
            JpsModuleRootModificationUtil.addDependency(module, library);
        }

        @Override
        protected JpsPackagingElement createPackagingElement(JpsModule module) {
            return new JpsKotlinCompilerOutputPackagingElement(module.createReference());
        }
    }
}