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

package org.jetbrains.k2js.translate.context;

import com.google.dart.compiler.backend.js.ast.JsArrayAccess;
import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.backend.js.ast.JsProgram;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.jet.lang.types.lang.PrimitiveType;
import org.jetbrains.k2js.config.Config;
import org.jetbrains.k2js.config.EcmaVersion;
import org.jetbrains.k2js.translate.declaration.ClassDeclarationTranslator;
import org.jetbrains.k2js.translate.expression.LiteralFunctionTranslator;
import org.jetbrains.k2js.translate.intrinsic.Intrinsics;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.kotlin.compiler.AnnotationsUtils;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.compiler.PredefinedAnnotation;
import org.jetbrains.kotlin.compiler.TranslationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.k2js.translate.utils.BindingUtils.isObjectDeclaration;
import static org.jetbrains.kotlin.compiler.AnnotationsUtils.*;

/**
 * Aggregates all the static parts of the context.
 */
public final class StaticContext {
    @NotNull
    private final JsProgram program;

    @NotNull
    private final BindingContext bindingContext;
    @NotNull
    private final Namer namer;

    @NotNull
    private final Intrinsics intrinsics;

    @NotNull
    private LiteralFunctionTranslator literalFunctionTranslator;
    @NotNull
    private ClassDeclarationTranslator classDeclarationTranslator;

    private final OverloadedMemberNameGenerator overloadedMemberNameGenerator = new OverloadedMemberNameGenerator();
    private final Map<VariableDescriptor, String> nameMap = new THashMap<VariableDescriptor, String>();
    private final Map<DeclarationDescriptor, JsExpression> qualifierMap = new THashMap<DeclarationDescriptor, JsExpression>();

    final Config configuration;

    public StaticContext(@NotNull BindingContext bindingContext, @NotNull Config configuration) {
        this.program = new JsProgram("main");
        this.bindingContext = bindingContext;
        this.namer = Namer.newInstance(program.getRootScope());
        this.intrinsics = new Intrinsics();
        this.configuration = configuration;
    }

    public void initTranslators(TranslationContext programContext) {
        literalFunctionTranslator = new LiteralFunctionTranslator(programContext);
        classDeclarationTranslator = new ClassDeclarationTranslator(programContext);
    }

    @NotNull
    public LiteralFunctionTranslator getLiteralFunctionTranslator() {
        return literalFunctionTranslator;
    }

    @NotNull
    public ClassDeclarationTranslator getClassDeclarationTranslator() {
        return classDeclarationTranslator;
    }

    public boolean isEcma5() {
        return configuration.getTarget() == EcmaVersion.v5;
    }

    @NotNull
    public JsProgram getProgram() {
        return program;
    }

    @NotNull
    public BindingContext getBindingContext() {
        return bindingContext;
    }

    @NotNull
    public Intrinsics getIntrinsics() {
        return intrinsics;
    }

    @NotNull
    public Namer getNamer() {
        return namer;
    }

    @NotNull
    public JsNameRef getQualifiedReference(@NotNull DeclarationDescriptor descriptor, @NotNull TranslationContext context) {
        ClassDescriptor classDescriptor;
        if (descriptor instanceof ConstructorDescriptor) {
            classDescriptor = ((ConstructorDescriptor) descriptor).getContainingDeclaration();
        }
        else if (descriptor instanceof ClassDescriptor) {
            classDescriptor = (ClassDescriptor) descriptor;
        }
        else {
            classDescriptor = null;
        }

        if (classDescriptor != null) {
            JsNameRef reference = classDeclarationTranslator.getQualifiedReference((classDescriptor));
            if (reference != null) {
                return reference;
            }
        }
        JsNameRef ref = getNameRefForDescriptor(descriptor, context);
        ref.setQualifier(getQualifierForDescriptor(descriptor));
        return ref;
    }

    @NotNull
    public String getNameForDescriptor(@NotNull VariableDescriptor descriptor, @Nullable TranslationContext context) {
        assert descriptor instanceof LocalVariableDescriptor || descriptor instanceof ValueParameterDescriptor;
        String name = nameMap.get(descriptor);
        if (name == null) {
            assert context != null;
            name = context.scope().declareFreshName(descriptor.getName().getName());
            nameMap.put(descriptor, name);
        }
        return name;
    }

    @NotNull
    public JsNameRef getNameRefForDescriptor(@NotNull DeclarationDescriptor descriptor, @Nullable TranslationContext context) {
        if (descriptor instanceof ConstructorDescriptor) {
            descriptor = ((ConstructorDescriptor) descriptor).getContainingDeclaration();
        }

        for (PredefinedAnnotation annotation : PredefinedAnnotation.values()) {
            AnnotationDescriptor annotationDescriptor = getAnnotationOrInsideAnnotatedClass(descriptor, annotation.getFQName());
            if (annotationDescriptor == null) {
                continue;
            }
            String name = getAnnotationStringParameter(annotationDescriptor);
            if (name == null) {
                name = descriptor.getName().getName();
            }
            return new JsNameRef(name);
        }

        if (isFromNativeModule(descriptor)) {
            return new JsNameRef(descriptor.getName().getName());
        }

        // property cannot be overloaded, so, name collision is not possible, we don't need create extra JsName and keep generated ref
        if (descriptor instanceof PropertyAccessorDescriptor) {
            PropertyAccessorDescriptor accessorDescriptor = (PropertyAccessorDescriptor) descriptor;
            if (accessorDescriptor.getReceiverParameter() != null) {
                return new JsNameRef(overloadedMemberNameGenerator.forExtensionProperty(accessorDescriptor));
            }

            String name = accessorDescriptor.getCorrespondingProperty().getName().getName();
            if (!isEcma5() && !isObjectDeclaration(bindingContext, accessorDescriptor.getCorrespondingProperty())) {
                name = Namer.getNameForAccessor(name, descriptor instanceof PropertyGetterDescriptor);
            }
            return new JsNameRef(name);
        }
        else if (descriptor instanceof SimpleFunctionDescriptor) {
            String name = overloadedMemberNameGenerator.forClassOrNamespaceFunction((SimpleFunctionDescriptor) descriptor);
            if (name != null) {
                return new JsNameRef(name);
            }
            //return localFunRefMap.get(descriptor);
        }
        else if (descriptor instanceof PropertyDescriptor) {
            return new JsNameRef(JsAstUtils.createNameForProperty((PropertyDescriptor) descriptor, isEcma5()));
        }
        else if (descriptor instanceof ClassDescriptor) {
            String standardName = getStandardObjectName((ClassDescriptor) descriptor);
            return new JsNameRef(standardName == null ? descriptor.getName().getName() : standardName);
        }
        else if (descriptor instanceof NamespaceDescriptor) {
            return new JsNameRef(Namer.generateNamespaceName(descriptor));
        }

        return new JsNameRef(getNameForDescriptor((VariableDescriptor) descriptor, context));
    }

    @Nullable
    public static String getStandardObjectName(@NotNull ClassDescriptor descriptor) {
        DeclarationDescriptor containingDeclaration = descriptor.getContainingDeclaration();
        if (containingDeclaration instanceof NamespaceDescriptor &&
            DescriptorUtils.getModuleDescriptor(containingDeclaration) == KotlinBuiltIns.getInstance().getBuiltInsModule()) {
            Name name = descriptor.getName();
            for (PrimitiveType type : PrimitiveType.NUMBER_TYPES) {
                if (type.getRangeTypeName().equals(name)) {
                    return "NumberRange";
                }
                else if (type.getProgressionClassName().shortName().equals(name)) {
                    return "NumberProgression";
                }
            }
            return name.getName();
        }
        return null;
    }

    @Nullable
    public JsExpression getQualifierForDescriptor(@NotNull DeclarationDescriptor descriptor) {
        if (descriptor instanceof ConstructorDescriptor) {
            descriptor = ((ConstructorDescriptor) descriptor).getContainingDeclaration();
        }

        DeclarationDescriptor namespace = descriptor.getContainingDeclaration();
        if (!(namespace instanceof NamespaceDescriptor) || descriptor instanceof PropertyDescriptor) {
            return null;
        }

        if (isLibraryObject(descriptor)) {
            return Namer.KOTLIN_OBJECT_NAME_REF;
        }

        ModuleDescriptor module = DescriptorUtils.getModuleDescriptor(namespace);
        if (module == KotlinBuiltIns.getInstance().getBuiltInsModule()) {
            return Namer.KOTLIN_OBJECT_NAME_REF;
        }

        if (isNativeModule(module) || AnnotationsUtils.isNativeByAnnotation(descriptor)) {
            return null;
        }
        return getPackageQualifiedName((NamespaceDescriptor) namespace, module);
    }

    private boolean isNativeModule(ModuleDescriptor descriptor) {
        return descriptor != configuration.getModule().getModuleDescriptor() && configuration.getModule().isDependencyProvided(descriptor);
    }

    public boolean isFromNativeModule(DeclarationDescriptor descriptor) {
        return isNativeModule(DescriptorUtils.getModuleDescriptor(descriptor));
    }

    @NotNull
    public JsExpression getPackageQualifiedName(NamespaceDescriptor namespace, @Nullable ModuleDescriptor moduleDescriptor) {
        JsExpression result = qualifierMap.get(namespace);
        if (result != null) {
            return result;
        }

        if (DescriptorUtils.isRootNamespace(namespace)) {
            result = getRootPackageQualifiedName(moduleDescriptor);
        }
        else {
            List<String> names = new ArrayList<String>();
            NamespaceDescriptor p = namespace;
            do {
                names.add(p.getName().getName());
                p = p.getContainingDeclaration() instanceof NamespaceDescriptor ? (NamespaceDescriptor) p.getContainingDeclaration() : null;
            }
            while (p != null && !DescriptorUtils.isRootNamespace(p));

            StringBuilder builder = new StringBuilder();
            for (int i = names.size() - 1; i >= 0; i--) {
                builder.append(names.get(i)).append('_');
            }
            result = new JsNameRef(builder.substring(0, builder.length() - 1), getRootPackageQualifiedName(moduleDescriptor));
        }
        qualifierMap.put(namespace, result);
        return result;
    }

    private JsExpression getRootPackageQualifiedName(@Nullable ModuleDescriptor moduleDescriptor) {
        if (moduleDescriptor == null || moduleDescriptor == configuration.getModule().getModuleDescriptor()) {
            return Namer.ROOT_PACKAGE_NAME_REF;
        }
        else {
            ModuleInfo dependency = configuration.getModule().findDependency(moduleDescriptor);
            if (dependency == null) {
                throw new TranslationException("Missed module dependency " + ModuleInfo.getNormalName(moduleDescriptor));
            }
            return new JsArrayAccess(Namer.kotlin("modules"), program.getStringLiteral(dependency.getName()));
        }
    }
}
