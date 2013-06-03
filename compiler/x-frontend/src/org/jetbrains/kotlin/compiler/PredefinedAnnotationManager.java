package org.jetbrains.kotlin.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;

public class PredefinedAnnotationManager {
    private static final FqName JS_PACKAGE_FQ_NAME = FqName.topLevel(Name.identifier("js"));

    private final ClassifierDescriptor nativeAnnotation;
    private final ClassifierDescriptor libraryAnnotation;
    private final ClassifierDescriptor enumerableAnnotation;
    private final ClassifierDescriptor optionsArgAnnotation;

    public PredefinedAnnotationManager(@NotNull ModuleInfo module) {
        NamespaceDescriptor jsPackage = module.getModuleDescriptor().getNamespace(JS_PACKAGE_FQ_NAME);
        assert jsPackage != null;

        nativeAnnotation = jsPackage.getMemberScope().getClassifier(Name.identifier("native"));
        libraryAnnotation = jsPackage.getMemberScope().getClassifier(Name.identifier("library"));
        enumerableAnnotation = jsPackage.getMemberScope().getClassifier(Name.identifier("enumerable"));
        optionsArgAnnotation = jsPackage.getMemberScope().getClassifier(Name.identifier("optionsArg"));
    }

    public boolean hasNative(@NotNull DeclarationDescriptor descriptor) {
        return findAnnotation(descriptor, nativeAnnotation, true) != null;
    }

    public boolean hasLibrary(@NotNull DeclarationDescriptor descriptor) {
        return findAnnotation(descriptor, libraryAnnotation, true) != null;
    }

    public boolean hasOptionsArg(@NotNull DeclarationDescriptor descriptor) {
        return findAnnotation(descriptor, optionsArgAnnotation, false) != null;
    }

    public boolean isNativeOrLibrary(@NotNull AnnotationDescriptor annotation) {
        ClassifierDescriptor classifier = annotation.getType().getConstructor().getDeclarationDescriptor();
        return nativeAnnotation.equals(classifier) || libraryAnnotation.equals(classifier);
    }

    public boolean isNativeOrLibrary(@NotNull DeclarationDescriptor declaration) {
        for (AnnotationDescriptor annotation : declaration.getAnnotations()) {
            if (isNativeOrLibrary(annotation)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEnumerable(@NotNull DeclarationDescriptor descriptor) {
        if (findAnnotation(descriptor, enumerableAnnotation, false) != null) {
            return true;
        }
        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        return containingClass != null &&
               ((containingClass.getKind().equals(ClassKind.OBJECT) && containingClass.getName().isSpecial()) ||
                findAnnotation(containingClass, enumerableAnnotation, false) != null);
    }

    @Nullable
    private static AnnotationDescriptor findAnnotation(
            @NotNull DeclarationDescriptor declaration,
            @NotNull ClassifierDescriptor annotationClass,
            boolean insideAnnotationClass
    ) {
        for (AnnotationDescriptor annotation : declaration.getAnnotations()) {
            if (annotationClass.equals(annotation.getType().getConstructor().getDeclarationDescriptor())) {
                return annotation;
            }
        }

        if (insideAnnotationClass) {
            ClassDescriptor containingClass = DescriptorUtils.getContainingClass(declaration);
            return containingClass == null ? null : findAnnotation(containingClass, annotationClass, true);
        }
        return null;
    }
}