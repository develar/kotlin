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

package org.jetbrains.jet.lang.resolve.java.data;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.resolve.java.scope.JavaPackageScope;
import org.jetbrains.jet.lang.resolve.name.FqName;

/**
 * Either package or class with static members
 */
public class ResolverNamespaceData extends ResolverScopeData {
    public static final ResolverNamespaceData NEGATIVE = new ResolverNamespaceData(true);

    private final NamespaceDescriptor namespaceDescriptor;

    private JavaPackageScope memberScope;

    public ResolverNamespaceData(
            @Nullable PsiClass psiClass,
            @Nullable PsiPackage psiPackage,
            @NotNull FqName fqName,
            @NotNull NamespaceDescriptor namespaceDescriptor
    ) {
        super(psiClass, psiPackage, fqName, true, namespaceDescriptor);
        this.namespaceDescriptor = namespaceDescriptor;
    }

    public ResolverNamespaceData(boolean negative) {
        super(negative);
        this.namespaceDescriptor = null;
    }

    public JavaPackageScope getMemberScope() {
        return memberScope;
    }

    public NamespaceDescriptor getNamespaceDescriptor() {
        return namespaceDescriptor;
    }

    public void setMemberScope(JavaPackageScope memberScope) {
        this.memberScope = memberScope;
    }
}
