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

package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.*;
import gnu.trove.THashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptorParent;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.*;

import static com.google.dart.compiler.backend.js.ast.JsVars.JsVar;

/**
 * @author Pavel Talanov
 */
public final class NamespaceDeclarationTranslator extends AbstractTranslator {
    @NotNull
    private final Iterable<JetFile> files;

    public static List<JsStatement> translateFiles(@NotNull Collection<JetFile> files, @NotNull TranslationContext context) {
        return new NamespaceDeclarationTranslator(files, context).translate();
    }

    private NamespaceDeclarationTranslator(@NotNull Iterable<JetFile> files, @NotNull TranslationContext context) {
        super(context);

        this.files = files;
    }

    @NotNull
    private List<JsStatement> translate() {
        THashMap<NamespaceDescriptor, NamespaceTranslator> descriptorToTranslator = new THashMap<NamespaceDescriptor, NamespaceTranslator>();

        final Map<NamespaceDescriptor, JsObjectLiteral> descriptorToDeclarationPlace = new THashMap<NamespaceDescriptor, JsObjectLiteral>();
        JsObjectLiteral rootNamespaceDefinition = null;

        ClassDeclarationTranslator classDeclarationTranslator = new ClassDeclarationTranslator(context());
        for (JetFile file : files) {
            NamespaceDescriptor descriptor = context().bindingContext().get(BindingContext.FILE_TO_NAMESPACE, file);
            assert descriptor != null;
            NamespaceTranslator translator = descriptorToTranslator.get(descriptor);
            if (translator == null) {
                if (rootNamespaceDefinition == null) {
                    rootNamespaceDefinition = getRootPackage(descriptorToDeclarationPlace, descriptor);
                }
                translator = new NamespaceTranslator(descriptor, classDeclarationTranslator, context());
                descriptorToTranslator.put(descriptor, translator);
            }

            translator.translate(file);
        }

        if (rootNamespaceDefinition == null) {
            return Collections.emptyList();
        }

        classDeclarationTranslator.generateDeclarations();

        final List<JsExpression> initializers = new ArrayList<JsExpression>();
        descriptorToTranslator.forEachValue(new TObjectProcedure<NamespaceTranslator>() {
            @Override
            public boolean execute(NamespaceTranslator translator) {
                translator.add(descriptorToDeclarationPlace, initializers);
                return true;
            }
        });

        List<JsStatement> result = new ArrayList<JsStatement>();
        JsVars vars = new JsVars(true);
        vars.addIfHasInitializer(context().literalFunctionTranslator().getDeclaration());
        vars.addIfHasInitializer(classDeclarationTranslator.getDeclaration());
        vars.addIfHasInitializer(getDeclaration(rootNamespaceDefinition));
        result.add(vars);

        for (JsExpression expression : initializers) {
            result.add(expression.makeStmt());
        }
        return result;
    }

    private static JsObjectLiteral getRootPackage(Map<NamespaceDescriptor, JsObjectLiteral> descriptorToDeclarationPlace,
            NamespaceDescriptor descriptor) {
        NamespaceDescriptor rootNamespace = descriptor;
        while (rootNamespace.getContainingDeclaration() instanceof NamespaceDescriptor) {
            rootNamespace = (NamespaceDescriptor) rootNamespace.getContainingDeclaration();
        }

        JsObjectLiteral rootNamespaceDefinition = new JsObjectLiteral(true);
        descriptorToDeclarationPlace.put(rootNamespace, rootNamespaceDefinition);
        return rootNamespaceDefinition;
    }

    private JsVar getDeclaration(@NotNull JsObjectLiteral rootNamespaceDefinition) {
        JsExpression packageMapValue;
        if (context().isEcma5()) {
            packageMapValue = new JsInvocation(JsAstUtils.CREATE_OBJECT, JsLiteral.NULL, rootNamespaceDefinition);
        }
        else {
            packageMapValue = rootNamespaceDefinition;
        }
        return new JsVar(context().scope().declareName("_").makeRef().getName(), packageMapValue);
    }
}
