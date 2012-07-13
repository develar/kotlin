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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.dart.compiler.backend.js.ast.JsVars.JsVar;

/**
 * @author Pavel Talanov
 */
public final class NamespaceDeclarationTranslator extends AbstractTranslator {
    @NotNull
    private final ClassDeclarationTranslator classDeclarationTranslator;
    @NotNull
    private final Iterable<JetFile> files;

    public static List<JsStatement> translateFiles(@NotNull Collection<JetFile> files, @NotNull TranslationContext context) {
        return new NamespaceDeclarationTranslator(files, context).translate();
    }

    private NamespaceDeclarationTranslator(@NotNull Iterable<JetFile> files, @NotNull TranslationContext context) {
        super(context);

        this.files = files;
        classDeclarationTranslator = new ClassDeclarationTranslator(context);
    }

    @NotNull
    private List<JsStatement> translate() {
        List<JsExpression> initializers = new ArrayList<JsExpression>();
        THashMap<NamespaceDescriptor, NamespaceTranslator> descriptorToTranslator = new THashMap<NamespaceDescriptor, NamespaceTranslator>();
        Map<NamespaceDescriptorParent, JsObjectLiteral> descriptorToDeclarationPlace = new THashMap<NamespaceDescriptorParent, JsObjectLiteral>();
        for (JetFile file : files) {
            NamespaceDescriptor descriptor = context().bindingContext().get(BindingContext.FILE_TO_NAMESPACE, file);
            assert descriptor != null;
            NamespaceTranslator translator = descriptorToTranslator.get(descriptor);
            if (translator == null) {
                translator = new NamespaceTranslator(descriptor, classDeclarationTranslator, context(), initializers, descriptorToDeclarationPlace);
                descriptorToTranslator.put(descriptor, translator);
            }

            translator.translate(file);
        }

        JsVar namespaces = declarationStatements(descriptorToTranslator);

        classDeclarationTranslator.generateDeclarations();

        List<JsStatement> result = new ArrayList<JsStatement>();
        JsVars vars = new JsVars(true);
        vars.addIfHasInitializer(context().literalFunctionTranslator().getDeclaration());
        vars.addIfHasInitializer(classDeclarationTranslator.getDeclaration());
        vars.addIfHasInitializer(namespaces);
        result.add(vars);

        initializeStatements(initializers, result);
        return result;
    }

    private JsVar declarationStatements(THashMap<NamespaceDescriptor, NamespaceTranslator> descriptorToTranslator) {
        final JsObjectLiteral objectLiteral = new JsObjectLiteral(true);
        JsNameRef packageMapNameRef = context().scope().declareName("_").makeRef();

        descriptorToTranslator.forEachValue(new TObjectProcedure<NamespaceTranslator>() {
            @Override
            public boolean execute(NamespaceTranslator translator) {
                getPlace(translator.descriptor.getContainingDeclaration())

                JsObjectLiteral place = descriptorToDeclarationPlace.get(translator.descriptor);
                if (place == null) {
                    place = new JsObjectLiteral();
                    descriptorToDeclarationPlace.get(translator.descriptor);
                    createPlace(descriptorToDeclarationPlace, translator.descriptor.getContainingDeclaration(), place, objectLiteral);
                }

                translator.addNamespaceDeclaration(objectLiteral.getPropertyInitializers());
                return true;
            }
        });

        JsExpression packageMapValue;
        if (context().isNotEcma3()) {
            packageMapValue = new JsInvocation(JsAstUtils.CREATE_OBJECT, JsLiteral.NULL, objectLiteral);
        }
        else {
            packageMapValue = objectLiteral;
        }
        return new JsVar(packageMapNameRef.getName(), packageMapValue);
    }

    private static JsObjectLiteral getPlace(THashMap<NamespaceDescriptor, JsObjectLiteral> descriptorToDeclarationPlace,
            NamespaceDescriptorParent descriptor, JsObjectLiteral child, JsObjectLiteral root) {
        if (descriptor instanceof NamespaceDescriptor) {
            JsObjectLiteral place = descriptorToDeclarationPlace.get(descriptor);
            if (place != null) {
                return;
            }

            place = new JsObjectLiteral();
            place.getPropertyInitializers().add(child);
        }
        else {

        }
    }

    private static void initializeStatements(@NotNull List<JsExpression> initializers,
            @NotNull List<JsStatement> statements) {
        for (JsExpression expression : initializers) {
            statements.add(expression.makeStmt());
        }
    }
}
