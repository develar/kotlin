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

package org.jetbrains.jet.plugin.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetNamedFunction;

import java.util.Collection;

/**
 * Stores package top level function (both extension and non-extension) full qualified names.
 */
public class JetTopLevelFunctionsFqnNameIndex extends StringStubIndexExtension<JetNamedFunction> {
    private static final StubIndexKey<String, JetNamedFunction> KEY = KotlinIndexUtil.createIndexKey(JetTopLevelFunctionsFqnNameIndex.class);

    private static final JetTopLevelFunctionsFqnNameIndex INSTANCE = new JetTopLevelFunctionsFqnNameIndex();

    public static JetTopLevelFunctionsFqnNameIndex getInstance() {
        return INSTANCE;
    }

    private JetTopLevelFunctionsFqnNameIndex() {}

    @NotNull
    @Override
    public StubIndexKey<String, JetNamedFunction> getKey() {
        return KEY;
    }

    @Override
    public Collection<JetNamedFunction> get(String s, Project project, @NotNull GlobalSearchScope scope) {
        return super.get(s, project, JetSourceFilterScope.kotlinSourcesAndLibraries(scope));
    }
}
