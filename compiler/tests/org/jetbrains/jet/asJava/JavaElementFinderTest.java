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

package org.jetbrains.jet.asJava;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.jet.JetLiteFixture;
import org.jetbrains.jet.JetTestUtils;
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment;
import org.jetbrains.jet.config.CommonConfigurationKeys;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.lazy.LazyResolveTestUtil;

import java.io.File;
import java.util.List;

public class JavaElementFinderTest extends JetLiteFixture {
    private JavaElementFinder finder;

    public JavaElementFinderTest() {
        super("asJava/findClasses");
    }

    @Override
    protected JetCoreEnvironment createEnvironment() {
        CompilerConfiguration configuration = new CompilerConfiguration();

        configuration.add(CommonConfigurationKeys.SOURCE_ROOTS_KEY, getTestDataPath() + "/asJava/findClasses/" + getTestName(false) + ".kt");

        return new JetCoreEnvironment(getTestRootDisposable(), configuration);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // We need to resolve all the files in order too fill in the trace that sits inside LightClassGenerationSupport
        List<String> paths = getEnvironment().getConfiguration().get(CommonConfigurationKeys.SOURCE_ROOTS_KEY);
        assert paths != null;
        List<JetFile> jetFiles = Lists.newArrayList();
        for (String path : paths) {
            jetFiles.add(JetTestUtils.loadJetFile(getProject(), new File(path)));
        }
        LazyResolveTestUtil.resolveEagerly(jetFiles, getEnvironment());
        finder = new JavaElementFinder(getProject(), LightClassGenerationSupport.getInstance(getProject()));
    }

    public void testFromEnumEntry() {
        assertClass("Direction");
        assertNoClass("Direction.NORTH");
        assertNoClass("Direction.SOUTH");
        assertNoClass("Direction.WEST");
        // TODO: assertClass("Direction.SOUTH.Hello");
        // TODO: assertClass("Direction.WEST.Some");
    }

    private void assertClass(String qualifiedName) {
        PsiClass psiClass = finder.findClass(qualifiedName, GlobalSearchScope.allScope(getProject()));
        assertNotNull(String.format("Class with fqn='%s' wasn't found.", qualifiedName), psiClass);
        assertTrue(String.format("Class with fqn='%s' is not valid.", qualifiedName), psiClass.isValid());
    }

    private void assertNoClass(String qualifiedName) {
        assertNull(String.format("Class with fqn='%s' isn't expected to be found.", qualifiedName),
                   finder.findClass(qualifiedName, GlobalSearchScope.allScope(getProject())));
    }
}
