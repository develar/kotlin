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

package org.jetbrains.jet.codegen;

import org.jetbrains.jet.ConfigurationKind;

import java.lang.reflect.Method;

public class ExtensionFunctionsTest extends CodegenTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        createEnvironmentWithMockJdkAndIdeaAnnotations(ConfigurationKind.JDK_ONLY);
    }

    @Override
    protected String getPrefix() {
        return "extensionFunctions";
    }

    public void testSimple() throws Exception {
        loadFile();
        final Method foo = generateFunction("foo");
        final Character c = (Character) foo.invoke(null);
        assertEquals('f', c.charValue());
    }

    public void testWhenFail() throws Exception {
        loadFile();
        Method foo = generateFunction("foo");
        assertThrows(foo, Exception.class, null, new StringBuilder());
    }

    public void testVirtual() {
        blackBoxFile("extensionFunctions/virtual.kt");
    }

    public void testShared() {
        blackBoxFile("extensionFunctions/shared.kt");
    }

    public void testKt475() {
        blackBoxFile("regressions/kt475.kt");
    }

    public void testKt865() {
        blackBoxFile("regressions/kt865.kt");
    }

    public void testKtNested2() {
        blackBoxFile("extensionFunctions/nested2.kt");
    }

    public void testKt606() {
        blackBoxFile("regressions/kt606.kt");
    }

    public void testKt1061() {
        blackBoxFile("regressions/kt1061.kt");
    }

    public void testKt1249() {
        blackBoxFile("regressions/kt1249.kt");
    }

    public void testKt1290() {
        blackBoxFile("regressions/kt1290.kt");
    }

    public void testKt1953() {
        blackBoxFile("regressions/kt1953.kt");
    }

    public void testKt1953Class() {
        blackBoxFile("regressions/kt1953_class.kt");
    }
}
