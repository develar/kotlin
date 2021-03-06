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

package org.jetbrains.jet.plugin.codeInsight;

import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
import org.jetbrains.jet.lang.ModuleConfiguration;
import org.jetbrains.jet.lang.descriptors.CallableMemberDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.psi.JetReferenceExpression;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingContextUtils;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.plugin.references.BuiltInsReferenceResolver;
import org.jetbrains.kotlin.compiler.ModuleInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class DescriptorToDeclarationUtil {
    private DescriptorToDeclarationUtil() {
    }

    @NotNull
    public static List<PsiElement> resolveToDeclarationPsiElements(
            @NotNull AnalyzeExhaust analyzeExhaust,
            @Nullable JetReferenceExpression referenceExpression
    ) {
        DeclarationDescriptor declarationDescriptor = analyzeExhaust.getBindingContext().get(BindingContext.REFERENCE_TARGET,
                                                                                             referenceExpression);
        if (declarationDescriptor == null) {
            return Collections.singletonList(analyzeExhaust.getBindingContext().get(BindingContext.LABEL_TARGET, referenceExpression));
        }

        BindingContext targetBindingContext = getBindingContextByDeclaration(analyzeExhaust, declarationDescriptor);
        return targetBindingContext == null
               ? Collections.<PsiElement>emptyList()
               : descriptorToDeclarations(targetBindingContext, declarationDescriptor);
    }

    @Nullable
    private static BindingContext getBindingContextByDeclaration(AnalyzeExhaust analyzeExhaust, DeclarationDescriptor declarationDescriptor) {
        ModuleConfiguration moduleConfiguration = analyzeExhaust.getModuleDescriptor().getModuleConfiguration();
        if (moduleConfiguration instanceof ModuleInfo) {
            ModuleDescriptor moduleDescriptor = DescriptorUtils.getModuleDescriptor(declarationDescriptor);
            return ((ModuleInfo) moduleConfiguration).findBindingContext(moduleDescriptor);
        }
        else {
            return analyzeExhaust.getBindingContext();
        }
    }

    public static PsiElement getDeclaration(JetFile file, DeclarationDescriptor descriptor, BindingContext bindingContext) {
        return getDeclaration(file.getProject(), descriptor, bindingContext);
    }

    public static PsiElement getDeclaration(Project project, DeclarationDescriptor descriptor, BindingContext bindingContext) {
        Collection<PsiElement> elements = BindingContextUtils.descriptorToDeclarations(bindingContext, descriptor);

        if (elements.isEmpty()) {
            BuiltInsReferenceResolver libraryReferenceResolver =
                    project.getComponent(BuiltInsReferenceResolver.class);
            elements = libraryReferenceResolver.resolveStandardLibrarySymbol(descriptor);
        }

        if (!elements.isEmpty()) {
            return elements.iterator().next();
        }

        return null;
    }

    @NotNull
    public static List<PsiElement> descriptorToDeclarations(@NotNull BindingContext context, @NotNull DeclarationDescriptor descriptor) {
        if (descriptor instanceof CallableMemberDescriptor) {
            return BindingContextUtils.callableDescriptorToDeclarations(context, (CallableMemberDescriptor) descriptor);
        }
        else {
            PsiElement psiElement = BindingContextUtils.descriptorToDeclaration(context, descriptor);
            if (psiElement != null) {
                return Lists.newArrayList(psiElement);
            }
            else {
                return Lists.newArrayList();
            }
        }
    }
}
