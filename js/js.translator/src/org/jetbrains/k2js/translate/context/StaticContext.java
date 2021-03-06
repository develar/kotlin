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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.dart.compiler.backend.js.ast.JsArrayAccess;
import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.backend.js.ast.JsStringLiteral;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.descriptors.impl.LocalVariableDescriptor;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.jet.lang.types.lang.PrimitiveType;
import org.jetbrains.k2js.config.Config;
import org.jetbrains.k2js.config.EcmaVersion;
import org.jetbrains.k2js.translate.expression.LiteralFunctionTranslator;
import org.jetbrains.k2js.translate.intrinsic.Intrinsics;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.kotlin.compiler.ModuleInfo;
import org.jetbrains.kotlin.compiler.PredefinedAnnotationManager;
import org.jetbrains.kotlin.compiler.TranslationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.k2js.translate.utils.BindingUtils.isObjectDeclaration;

public final class StaticContext {
    @NotNull
    private final BindingContext bindingContext;

    @NotNull
    private final Intrinsics intrinsics;

    @NotNull
    private LiteralFunctionTranslator literalFunctionTranslator;

    private final OverloadedMemberNameGenerator overloadedMemberNameGenerator = new OverloadedMemberNameGenerator();
    private final BiMap<DeclarationDescriptor, String> nameMap = HashBiMap.create();
    private final Map<DeclarationDescriptor, JsExpression> qualifierMap = new THashMap<DeclarationDescriptor, JsExpression>();

    final Config configuration;
    final PredefinedAnnotationManager predefinedAnnotationManager;

    public StaticContext(@NotNull BindingContext bindingContext, @NotNull Config configuration) {
        this.bindingContext = bindingContext;
        this.intrinsics = new Intrinsics();
        this.configuration = configuration;
        predefinedAnnotationManager = new PredefinedAnnotationManager(configuration.getModule());
    }

    public void initTranslators(TranslationContext programContext) {
        literalFunctionTranslator = new LiteralFunctionTranslator(programContext);
    }

    @NotNull
    public LiteralFunctionTranslator getLiteralFunctionTranslator() {
        return literalFunctionTranslator;
    }

    public boolean isEcma5() {
        return configuration.getTarget() == EcmaVersion.v5;
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
    public JsExpression getQualifiedReference(@NotNull DeclarationDescriptor descriptor, @NotNull TranslationContext context) {
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
            JsNameRef ref = getNameRef(classDescriptor);
            ref.setQualifier(context.getQualifierForDescriptor(classDescriptor));
            return ref;

        }
        if (descriptor instanceof NamespaceDescriptor) {
            return getPackageQualifiedName((NamespaceDescriptor) descriptor, null);
        }

        JsNameRef ref = getNameRef(descriptor);
        ref.setQualifier(getQualifierForDescriptor(descriptor));
        return ref;
    }

    @NotNull
    public String getName(@NotNull CallableDescriptor descriptor) {
        assert descriptor instanceof LocalVariableDescriptor ||
               descriptor instanceof ValueParameterDescriptor ||
               descriptor instanceof SimpleFunctionDescriptor /* only local named function */;
        String name = nameMap.get(descriptor);
        if (name == null) {
            String suggestedName = name = descriptor.getName().asString();
            int counter = 0;
            while (nameMap.containsValue(name)) {
                name = suggestedName + '_' + counter++;
            }
            nameMap.put(descriptor, name);
        }
        return name;
    }

    @Nullable
    private JsNameRef getNameIfNative(DeclarationDescriptor descriptor) {
        for (AnnotationDescriptor annotation : descriptor.getAnnotations()) {
            if (predefinedAnnotationManager.isNative(annotation)) {
                String name = predefinedAnnotationManager.getNativeName(annotation);
                return new JsNameRef(name == null ? descriptor.getName().asString() : name);
            }
        }

        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        if (containingClass != null) {
            for (AnnotationDescriptor annotation : containingClass.getAnnotations()) {
                if (predefinedAnnotationManager.isNative(annotation)) {
                    return new JsNameRef(descriptor.getName().asString());
                }
            }
        }

        if (isFromNativeModule(descriptor)) {
            return new JsNameRef(descriptor.getName().asString());
        }

        return null;
    }

    @NotNull
    public JsNameRef getNameRef(@NotNull DeclarationDescriptor descriptor) {
        if (descriptor instanceof ConstructorDescriptor) {
            descriptor = ((ConstructorDescriptor) descriptor).getContainingDeclaration();
        }

        JsNameRef nameRef = getNameIfNative(descriptor);
        if (nameRef != null) {
            return nameRef;
        }

        // property cannot be overloaded, so, name collision is not possible, we don't need create extra JsName and keep generated ref
        if (descriptor instanceof PropertyAccessorDescriptor) {
            PropertyAccessorDescriptor accessorDescriptor = (PropertyAccessorDescriptor) descriptor;
            if (accessorDescriptor.getReceiverParameter() != null) {
                return new JsNameRef(overloadedMemberNameGenerator.forExtensionProperty(accessorDescriptor));
            }

            String name = accessorDescriptor.getCorrespondingProperty().getName().asString();
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
        }
        else if (descriptor instanceof PropertyDescriptor) {
            return new JsNameRef(JsAstUtils.createNameForProperty((PropertyDescriptor) descriptor, isEcma5()));
        }
        else if (descriptor instanceof ClassDescriptor) {
            String standardName = getStandardObjectName((ClassDescriptor) descriptor);
            return new JsNameRef(standardName == null ? descriptor.getName().asString() : standardName);
        }
        else if (descriptor instanceof NamespaceDescriptor) {
            return new JsNameRef(Namer.generateNamespaceName(descriptor));
        }

        return new JsNameRef(getName((CallableDescriptor) descriptor));
    }

    @Nullable
    private static String getStandardObjectName(@NotNull ClassDescriptor descriptor) {
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
            return name.asString();
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

        AnnotationDescriptor nativeAnnotation = predefinedAnnotationManager.getNative(descriptor);
        if (nativeAnnotation != null) {
            String qualifier = predefinedAnnotationManager.getNativeQualifier(nativeAnnotation);
            if (qualifier == null) {
                return null;
            }
            if (qualifier.equals(Namer.KOTLIN_OBJECT_NAME)) {
                return Namer.KOTLIN_OBJECT_NAME_REF;
            }
            else {
                return qualifier.equals("stdlib") ? Namer.JS_STDLIB_PACKAGE_REF : new JsNameRef(qualifier);
            }
        }

        ModuleDescriptor module = DescriptorUtils.getModuleDescriptor(namespace);
        if (module == KotlinBuiltIns.getInstance().getBuiltInsModule()) {
            return Namer.JS_STDLIB_PACKAGE_REF;
        }

        if (isNativeModule(module) || predefinedAnnotationManager.hasNative(descriptor)) {
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
                names.add(p.getName().asString());
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
            return new JsArrayAccess(Namer.MODULES_NAME_REF, new JsStringLiteral(dependency.getName()));
        }
    }

    public void clearNameMapping() {
        nameMap.clear();
    }
}