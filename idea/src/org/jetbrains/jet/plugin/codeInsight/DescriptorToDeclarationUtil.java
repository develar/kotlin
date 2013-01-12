package org.jetbrains.jet.plugin.codeInsight;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.analyzer.AnalyzeExhaust;
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
        if (analyzeExhaust.getModuleConfiguration() instanceof ModuleInfo) {
            ModuleDescriptor moduleDescriptor = DescriptorUtils.getParentOfType(declarationDescriptor, ModuleDescriptor.class);
            if (moduleDescriptor == null) {
                return null;
            }

            for (ModuleInfo moduleInfo : ((ModuleInfo) analyzeExhaust.getModuleConfiguration()).getDependencies()) {
                if (moduleInfo.getModuleDescriptor().equals(moduleDescriptor)) {
                    return moduleInfo.bindingContext;
                }
            }
            return null;
        }
        else {
            return analyzeExhaust.getBindingContext();
        }
    }

    public static PsiElement getDeclaration(JetFile file, DeclarationDescriptor descriptor, BindingContext bindingContext) {
        Collection<PsiElement> elements = descriptorToDeclarations(bindingContext, descriptor);

        if (elements.isEmpty()) {
            elements = BuiltInsReferenceResolver.getInstance(file.getProject()).resolveStandardLibrarySymbol(descriptor);
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
