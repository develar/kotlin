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
import com.google.dart.compiler.util.AstUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.Modality;
import org.jetbrains.jet.lang.psi.JetClass;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.utils.ClassSortingUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.ArrayList;
import java.util.List;

import static com.google.dart.compiler.backend.js.ast.JsVars.JsVar;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getClassDescriptor;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.newBlock;

/**
 * @author Pavel Talanov
 *         <p/>
 *         Generates a big block where are all the classes(objects representing them) are created.
 */
public final class ClassDeclarationTranslator extends AbstractTranslator {
    private int localNameCounter;

    @NotNull
    private final List<ClassDescriptor> descriptors = new ArrayList<ClassDescriptor>();

    @NotNull
    private final THashMap<JetClass, JsNameRef> classToLabel = new THashMap<JetClass, JsNameRef>();

    @NotNull
    private final JsFunction dummyFunction;

    private final JsName declarationsObject;
    private final JsVar classesVar;

    public ClassDeclarationTranslator(@NotNull TranslationContext context) {
        super(context);

        dummyFunction = new JsFunction(context.jsScope());
        declarationsObject = context().jsScope().declareName(Namer.nameForClassesVariable());
        classesVar = new JsVars.JsVar(declarationsObject);
    }

    @NotNull
    public JsVars getDeclarationsStatement() {
        JsVars vars = new JsVars();
        vars.add(classesVar);
        return vars;
    }

    public void generateDeclarations() {
        JsObjectLiteral valueLiteral = new JsObjectLiteral();
        List<JetClass> declarations = ClassSortingUtils.sortUsingInheritanceOrder(descriptors, bindingContext());
        JsVars vars = new JsVars();
        for (JetClass declaration : declarations) {
            JsNameRef label = classToLabel.get(declaration);
            JsExpression definition = Translation.translateClassDeclaration(declaration, classToLabel, context());
            JsExpression value;
            if (label.getName() == null) {
                value = definition;
            }
            else {
                vars.add(new JsVar(label.getName(), definition));
                value = label;
            }

            valueLiteral.getPropertyInitializers().add(new JsPropertyInitializer(label, value));
        }

        if (vars.isEmpty()) {
            classesVar.setInitExpr(valueLiteral);
            return;
        }

        List<JsStatement> result = new ArrayList<JsStatement>();
        result.add(vars);
        result.add(new JsReturn(valueLiteral));
        dummyFunction.setBody(newBlock(result));
        classesVar.setInitExpr(AstUtil.newInvocation(dummyFunction));
    }

    @NotNull
    public JsPropertyInitializer translateAndGetClassNameToClassObject(@NotNull JetClass declaration) {
        ClassDescriptor descriptor = getClassDescriptor(context().bindingContext(), declaration);
        descriptors.add(descriptor);
        JsNameRef labelRef;
        String label = 'c' + Integer.toString(localNameCounter++, 36);
        if (descriptor.getModality() == Modality.FINAL) {
            labelRef = new JsNameRef(label);
        }
        else {
            labelRef = dummyFunction.getScope().declareName(label).makeRef();
        }

        classToLabel.put(declaration, labelRef);

        JsNameRef qualifiedLabelRef = new JsNameRef(labelRef.getIdent());
        qualifiedLabelRef.setQualifier(declarationsObject.makeRef());
        JsExpression value;
        if (context().isEcma5()) {
            value = JsAstUtils.createDataDescriptor(qualifiedLabelRef, false, context());
        }
        else {
            value = qualifiedLabelRef;
        }

        return new JsPropertyInitializer(context().program().getStringLiteral(descriptor.getName().getName()), value);
    }
}
