package org.jetbrains.kotlin.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.constants.CompileTimeConstant;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;

public class PredefinedAnnotationManager {
    private static final FqName JS_PACKAGE_FQ_NAME = FqName.topLevel(Name.identifier("js"));

    private final ClassDescriptor nativeAnnotation;
    private final ClassifierDescriptor enumerableAnnotation;
    private final ClassifierDescriptor optionsArgAnnotation;

    private final ValueParameterDescriptor nativeNameParameter;
    private final ValueParameterDescriptor nativeQualifierParameter;

    public PredefinedAnnotationManager(@NotNull ModuleInfo module) {
        NamespaceDescriptor jsPackage = module.getModuleDescriptor().getNamespace(JS_PACKAGE_FQ_NAME);
        assert jsPackage != null;

        nativeAnnotation = (ClassDescriptor) jsPackage.getMemberScope().getClassifier(Name.identifier("native"));
        enumerableAnnotation = jsPackage.getMemberScope().getClassifier(Name.identifier("enumerable"));
        optionsArgAnnotation = jsPackage.getMemberScope().getClassifier(Name.identifier("optionsArg"));

        nativeNameParameter = DescriptorUtils.getValueParameterDescriptorForAnnotationParameter(Name.identifier("name"), nativeAnnotation);
        nativeQualifierParameter = DescriptorUtils.getValueParameterDescriptorForAnnotationParameter(Name.identifier("qualifier"), nativeAnnotation);
    }

    public boolean hasNative(@NotNull DeclarationDescriptor descriptor) {
        return getNative(descriptor) != null;
    }

    @Nullable
    public AnnotationDescriptor getNative(DeclarationDescriptor descriptor) {
        return findAnnotation(descriptor, nativeAnnotation, true);
    }

    @Nullable
    public String getNativeName(@NotNull AnnotationDescriptor nativeAnnotation) {
        return getNativeParameterValue(nativeAnnotation, nativeNameParameter);
    }

    @Nullable
    public String getNativeQualifier(@NotNull AnnotationDescriptor nativeAnnotation) {
        return getNativeParameterValue(nativeAnnotation, nativeQualifierParameter);
    }

    @Nullable
    private static String getNativeParameterValue(AnnotationDescriptor nativeAnnotation, ValueParameterDescriptor valueParameterDescriptor) {
        CompileTimeConstant<?> qualifier = nativeAnnotation.getValueArgument(valueParameterDescriptor);
        return qualifier == null ? null : (String) qualifier.getValue();
    }

    public boolean isNativeButNotFromKotlin(@NotNull CallableDescriptor declaration) {
        AnnotationDescriptor annotation = findAnnotation(declaration, nativeAnnotation, false);
        if (annotation != null) {
            return !"Kotlin".equals(getNativeQualifier(annotation));
        }

        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(declaration);
        return containingClass != null && findAnnotation(containingClass, nativeAnnotation, true) != null;
    }

    public boolean hasOptionsArg(@NotNull DeclarationDescriptor descriptor) {
        return findAnnotation(descriptor, optionsArgAnnotation, false) != null;
    }

    public boolean isNative(@NotNull AnnotationDescriptor annotation) {
        return nativeAnnotation.equals(annotation.getType().getConstructor().getDeclarationDescriptor());
    }

    public boolean isNative(@NotNull DeclarationDescriptor declaration) {
        for (AnnotationDescriptor annotation : declaration.getAnnotations()) {
            if (isNative(annotation)) {
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