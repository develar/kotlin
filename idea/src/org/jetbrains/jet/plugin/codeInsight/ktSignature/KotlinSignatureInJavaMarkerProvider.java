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

package org.jetbrains.jet.plugin.codeInsight.ktSignature;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.di.InjectorForJavaSemanticServices;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.java.JavaDescriptorResolver;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.plugin.JetIcons;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.jet.plugin.codeInsight.ktSignature.KotlinSignatureUtil.*;

public class KotlinSignatureInJavaMarkerProvider implements LineMarkerProvider {
    private static final String SHOW_MARKERS_PROPERTY = "kotlin.signature.markers.enabled";

    private static final GutterIconNavigationHandler<PsiModifierListOwner> NAVIGATION_HANDLER = new GutterIconNavigationHandler<PsiModifierListOwner>() {
        @Override
        public void navigate(MouseEvent e, PsiModifierListOwner element) {
            if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED)) {
                new EditSignatureAction(element).actionPerformed(DataManager.getInstance().getDataContext(e.getComponent()), e.getPoint());
            }
        }
    };


    @Override
    @Nullable
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
        if (elements.isEmpty()) {
            return;
        }

        Project project = elements.get(0).getProject();
        if (!isMarkersEnabled(project)) {
            return;
        }

        InjectorForJavaSemanticServices injector = new InjectorForJavaSemanticServices(project);
        JavaDescriptorResolver javaDescriptorResolver = injector.getJavaDescriptorResolver();
        BindingContext bindingContext = injector.getBindingTrace().getBindingContext();

        for (PsiElement element : elements) {
            if (!(element instanceof PsiMember)) {
                continue;
            }

            PsiMember member = (PsiMember) element;
            if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
                continue;
            }

            PsiClass containingClass = member.getContainingClass();
            if (containingClass == null) { // e.g., type parameter
                continue;
            }

            String qualifiedName = containingClass.getQualifiedName();
            assert qualifiedName != null;

            FqName classFqName = new FqName(qualifiedName);
            ClassDescriptor klass = javaDescriptorResolver.resolveClass(classFqName);
            assert klass != null : " Couldn't find class " + classFqName;

            String memberNameAsString = member.getName();
            assert memberNameAsString != null;

            Name name = Name.identifier(memberNameAsString);
            if (element instanceof PsiMethod) {
                klass.getDefaultType().getMemberScope().getFunctions(name);
            }
            else if (element instanceof PsiField) {
                klass.getDefaultType().getMemberScope().getProperties(name);
            }
            else {
                continue;
            }

            PsiModifierListOwner annotationOwner = getAnnotationOwner(element);
            DeclarationDescriptor memberDescriptor = bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, annotationOwner);
            assert memberDescriptor != null : "Couldn't find descriptor for " + annotationOwner;
            List<String> errors = bindingContext.get(BindingContext.LOAD_FROM_JAVA_SIGNATURE_ERRORS, memberDescriptor);
            boolean hasSignatureAnnotation = findKotlinSignatureAnnotation(element) != null;

            if (errors != null || hasSignatureAnnotation) {
                result.add(new MyLineMarkerInfo((PsiModifierListOwner) element, errors, hasSignatureAnnotation));
            }
        }
    }

    public static boolean isMarkersEnabled(@NotNull Project project) {
        return PropertiesComponent.getInstance(project).getBoolean(SHOW_MARKERS_PROPERTY, true);
    }

    public static void setMarkersEnabled(@NotNull Project project, boolean value) {
        PropertiesComponent.getInstance(project).setValue(SHOW_MARKERS_PROPERTY, Boolean.toString(value));
        refreshMarkers(project);
    }

    private static class MyLineMarkerInfo extends LineMarkerInfo<PsiModifierListOwner> {
        public MyLineMarkerInfo(PsiModifierListOwner element, @Nullable List<String> errors, boolean hasAnnotation) {
            super(element, element.getTextOffset(), errors != null ? AllIcons.Ide.Error : JetIcons.SMALL_LOGO, Pass.UPDATE_ALL,
                  new TooltipProvider(errors), hasAnnotation ? NAVIGATION_HANDLER : null);
        }

        @Nullable
        @Override
        public GutterIconRenderer createGutterRenderer() {
            return new LineMarkerGutterIconRenderer<PsiModifierListOwner>(this) {
                @Nullable
                @Override
                public ActionGroup getPopupMenuActions() {
                    PsiModifierListOwner element = getElement();
                    assert element != null;

                    return new DefaultActionGroup(new EditSignatureAction(element), new DeleteSignatureAction(element));
                }
            };
        }
    }

    private static class TooltipProvider implements Function<PsiElement, String> {
        private final @Nullable List<String> errors;

        private TooltipProvider(@Nullable List<String> errors) {
            this.errors = errors;
        }

        @Nullable
        @Override
        public String fun(PsiElement element) {
            PsiAnnotation annotation = findKotlinSignatureAnnotation(element);

            if (annotation == null) return errorsString();

            String signature = getKotlinSignature(annotation);
            String text = "Alternative Kotlin signature is available for this method:\n"
                       + StringUtil.escapeXml(signature);
            if (errors == null) {
                return text;
            }
            return text + "\nIt has the following " + StringUtil.pluralize("error", errors.size()) + ":\n"
                   + errorsString();
        }

        @NotNull
        private String errorsString() {
            assert errors != null;
            return StringUtil.escapeXml(StringUtil.join(errors, "\n"));
        }
    }
}
