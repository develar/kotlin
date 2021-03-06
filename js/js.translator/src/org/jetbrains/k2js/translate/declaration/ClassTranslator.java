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

package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.FunctionTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.BindingUtils.getClassDescriptor;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.assignment;
import static org.jetbrains.k2js.translate.utils.TranslationUtils.assignmentToBackingField;

public final class ClassTranslator {
    private static final JsNameRef DESCRIPTOR_VAR_REF = new JsNameRef("classDescriptor$");
    private static final JsNameRef INITIALIZED_VAR_REF = new JsNameRef("classInitialized$");
    private static final JsNameRef PROTO_VAR_REF = new JsNameRef("proto$");
    private static final List<JsParameter> INIT_INHERITOR_FUN_PARAMETERS =
            Collections.singletonList(new JsParameter(PROTO_VAR_REF.getName()));

    private static final JsObjectLiteral CREATE_EMPTY_PROTO_OBJECT = new JsObjectLiteral(Collections.<JsPropertyInitializer>emptyList());

    private static final String INIT_INHERITOR_FUN_NAME = "initInheritor$";

    private final JetClassOrObject declaration;
    private final ClassDescriptor descriptor;
    private final JsFunction closure;

    private int classPrototypeInitStatementIndex;
    private final JsNameRef constructorFunRef;

    private final List<JsNode> definitionNodes;

    private JsFunction constructor;

    public ClassTranslator(JetClassOrObject declaration, ClassDescriptor descriptor, JsFunction closure) {
        this.declaration = declaration;
        this.descriptor = descriptor;
        this.closure = closure;
        this.definitionNodes = closure.getBody().getStatements();
        constructorFunRef = new JsNameRef(descriptor.getName().isSpecial() ? "A$" : descriptor.getName().asString());
    }

    public JsFunction getConstructor() {
        return constructor;
    }

    public static JsExpression translateObjectDeclaration(@NotNull JetObjectDeclaration declaration, @NotNull TranslationContext context) {
        return translateObjectDeclaration(declaration, getClassDescriptor(context.bindingContext(), declaration), context);
    }

    @NotNull
    public static JsExpression translateObjectDeclaration(
            @NotNull JetObjectDeclaration objectDeclaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull TranslationContext context
    ) {
        ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
        if (containingClass == null) {
            return new JsNew(translate(objectDeclaration, descriptor, context, false));
        }
        return context.literalFunctionTranslator().translate(containingClass, context, objectDeclaration, descriptor);
    }

    @NotNull
    public static JsInvocation translate(
            @NotNull JetClassOrObject declaration,
            @NotNull ClassDescriptor descriptor,
            @NotNull TranslationContext context,
            boolean asDataDescriptor
    ) {
        JsFunction closure = JsAstUtils.createFunctionWithEmptyBody(context.scope());
        return new ClassTranslator(declaration, descriptor, closure).translate(context.contextWithScope(closure), asDataDescriptor);
    }

    public JsInvocation translate(TranslationContext declarationContext, boolean asDataDescriptor) {
        declarationContext.literalFunctionTranslator().setDefinitionPlace(new DefinitionPlace(definitionNodes));
        addClassOwnDeclarations(declarationContext);
        declarationContext.literalFunctionTranslator().popDefinitionPlace();

        definitionNodes.add(new JsReturn(asDataDescriptor
                                         ? JsAstUtils.toDataDescriptor(constructorFunRef, declarationContext)
                                         : constructorFunRef));
        return new JsInvocation(new JsInvocation(null, closure), Collections.<JsExpression>emptyList());
    }

    private void addClassOwnDeclarations(@NotNull TranslationContext declarationContext) {
        List<JsPropertyInitializer> members = new SmartList<JsPropertyInitializer>();
        DeclarationBodyVisitor visitor = new DeclarationBodyVisitor(members, declarationContext);
        constructor = visitor.getInitializer();
        boolean isTrait = descriptor.getKind().equals(ClassKind.TRAIT);
        if (!isTrait) {
            List<JsNode> constructorStatements = constructor.getBody().getStatements();
            classPrototypeInitStatementIndex = constructorStatements.size();
            constructorStatements.add(JsStatement.EMPTY);

            // must be before visitor.traverseContainer - visitAnonymousInitializer will add anonymous constructor, but such statement can use constructor properties,
            // so, we should process constructor properties first of all
            constructor.setParameters(translateConstructorParameters(visitor.getInitializerContext(), constructor, members,
                                                                     declarationContext));
        }
        visitor.traverseContainer(declaration);

        JsExpression membersDescriptor;
        if (members.isEmpty()) {
            membersDescriptor = JsLiteral.NULL;
        }
        else {
            // todo jsdoc lends
            //if (!descriptor.getKind().isObject()) {
            //    // about "prototype" — see http://code.google.com/p/jsdoc-toolkit/wiki/TagLends
            //    invocationArguments.add(new JsDocComment(JsAstUtils.LENDS_JS_DOC_TAG, new JsNameRef("prototype", declarationContext
            //            .getQualifiedReference(descriptor))));
            //}
            membersDescriptor = new JsObjectLiteral(members, true);
        }

        constructor.setName(constructorFunRef.getName());
        int beforeConstructorIndex = definitionNodes.size();
        definitionNodes.add(JsStatement.EMPTY);
        definitionNodes.add(constructor);
        addClassInitialization(membersDescriptor, declarationContext, beforeConstructorIndex);
    }

    /**
     * We use classDescriptor$ as init marker (classDescriptor$ !== null) if:
     * 1) class is final;
     * 2) class has members.
     * Otherwise we use classInitialized$.
     *
     * But if class doesn't have members and supertypes - we don't use these variables
     */
    private void addClassInitialization(
            JsExpression membersDescriptor,
            TranslationContext context,
            int beforeConstructorIndex
    ) {
        List<JsNode> constructorStatements = constructor.getBody().getStatements();
        List<JsNode> initStatements = null;

        List<JsExpression> superTypeRefs = null;

        SmartList<JsVar> closureVars = new SmartList<JsVar>();
        ClassDescriptor anyClass = KotlinBuiltIns.getInstance().getAny();
        boolean useProtoDescriptorAsInitMarker = true;
        JsExpression lastInitStatement = null;
        for (JetType type : descriptor.getTypeConstructor().getSupertypes()) {
            ClassDescriptor superClass = DescriptorUtils.getClassDescriptorForType(type);
            if (superClass.equals(anyClass)) {
                continue;
            }

            if (initStatements == null) {
                if (descriptor.getKind() == ClassKind.TRAIT) {
                    initStatements = new SmartList<JsNode>();
                }
                else {
                    initStatements = new ArrayList<JsNode>();
                    if (descriptor.getModality() != Modality.FINAL || membersDescriptor == JsLiteral.NULL) {
                        closureVars.add(new JsVar(INITIALIZED_VAR_REF.getName(), JsLiteral.FALSE));
                        useProtoDescriptorAsInitMarker = false;
                        lastInitStatement = assignment(INITIALIZED_VAR_REF, JsLiteral.TRUE);
                    }
                    else {
                        lastInitStatement = assignment(DESCRIPTOR_VAR_REF, JsLiteral.NULL);
                    }


                    initStatements.add(new JsVars(new JsVar(PROTO_VAR_REF.getName(), CREATE_EMPTY_PROTO_OBJECT)));
                }

                superTypeRefs = new SmartList<JsExpression>();
            }

            JsExpression superTypeRef = context.getQualifiedReference(superClass);
            superTypeRefs.add(superTypeRef);
            initStatements.add(new JsInvocation(new JsNameRef(INIT_INHERITOR_FUN_NAME, superTypeRef), PROTO_VAR_REF));

            if (superClass.getKind() == ClassKind.CLASS) {
                for (JetDelegationSpecifier specifier : declaration.getDelegationSpecifiers()) {
                    if (specifier instanceof JetDelegatorToSuperCall) {
                        //addCallToSuperMethod((JetDelegatorToSuperCall) specifier, superClass, initializer, context);
                        break;
                    }
                }
            }
        }

        boolean hasMembers = membersDescriptor != JsLiteral.NULL;
        if (hasMembers && (descriptor.getModality() != Modality.FINAL || initStatements != null)) {
            closureVars.add(new JsVar(DESCRIPTOR_VAR_REF.getName(), membersDescriptor));
        }

        if (!closureVars.isEmpty()) {
            definitionNodes.set(beforeConstructorIndex, new JsVars(closureVars));
        }

        JsNameRef funProtoRef = new JsNameRef("prototype", constructorFunRef);
        List<JsNode> initInheritorFunStatements = null;
        if (initStatements == null) {
            if (hasMembers) {
                JsExpression prototype;
                if (descriptor.getModality() == Modality.FINAL) {
                    prototype = membersDescriptor;
                }
                else {
                    prototype = DESCRIPTOR_VAR_REF;
                    initInheritorFunStatements =
                            Collections.<JsNode>singletonList(new JsInvocation(JsAstUtils.DEFINE_PROPERTIES, PROTO_VAR_REF, prototype));
                }

                if (context.isEcma5()) {
                    prototype = new JsInvocation(JsAstUtils.CREATE_OBJECT, JsLiteral.NULL, prototype);
                }
                definitionNodes.add(assignment(funProtoRef, prototype));
            }
            else if (descriptor.getModality() != Modality.FINAL) {
                initInheritorFunStatements = Collections.emptyList();
            }
            definitionNodes.add(assignment(new JsNameRef("superTypes$", constructorFunRef), JsLiteral.NULL));
        }
        else {
            if (hasMembers) {
                initStatements.add(new JsInvocation(JsAstUtils.DEFINE_PROPERTIES, PROTO_VAR_REF, DESCRIPTOR_VAR_REF));
            }

            if (descriptor.getKind() == ClassKind.TRAIT) {
                initInheritorFunStatements = initStatements;
            }
            else {
                int initInheritorSubListSize = initStatements.size();
                initStatements.add(lastInitStatement);
                initStatements.add(assignment(new JsNameRef("__proto__", JsLiteral.THIS), assignment(funProtoRef, PROTO_VAR_REF)));

                initStatements.add(assignment(new JsNameRef("superTypes$", constructorFunRef), new JsArrayLiteral(superTypeRefs)));

                JsExpression isNotInitialized = useProtoDescriptorAsInitMarker
                                                ? JsAstUtils.inequality(DESCRIPTOR_VAR_REF, JsLiteral.NULL)
                                                : JsAstUtils.not(INITIALIZED_VAR_REF);
                constructorStatements.set(classPrototypeInitStatementIndex, new JsIf(isNotInitialized, new JsBlock(initStatements)));

                if (descriptor.getModality() != Modality.FINAL) {
                    initInheritorFunStatements = initStatements.subList(1, initInheritorSubListSize);
                }
            }
        }

        if (initInheritorFunStatements != null) {
            JsFunction initInheritorFun = new JsFunction(null, new JsBlock(initInheritorFunStatements));
            initInheritorFun.setParameters(INIT_INHERITOR_FUN_PARAMETERS);
            definitionNodes.add(assignment(new JsNameRef(INIT_INHERITOR_FUN_NAME, constructorFunRef), initInheritorFun));
        }
    }

    private static void mayBeAddCallToSuperMethod(
            ClassDescriptor descriptor,
            JetClassOrObject declaration,
            JsFunction initializer,
            TranslationContext context
    ) {
        for (JetType type : descriptor.getTypeConstructor().getSupertypes()) {
            ClassDescriptor superClassDescriptor = DescriptorUtils.getClassDescriptorForType(type);
            if (superClassDescriptor.getKind() == ClassKind.CLASS) {
                for (JetDelegationSpecifier specifier : declaration.getDelegationSpecifiers()) {
                    if (specifier instanceof JetDelegatorToSuperCall) {
                        addCallToSuperMethod((JetDelegatorToSuperCall) specifier, superClassDescriptor, initializer, context);
                        return;
                    }
                }
                return;
            }
        }
    }

    private static void addCallToSuperMethod(
            @NotNull JetDelegatorToSuperCall superCall,
            @NotNull ClassDescriptor superClassDescriptor,
            @NotNull JsFunction constructor,
            @NotNull TranslationContext context
    ) {
        List<? extends ValueArgument> arguments = superCall.getValueArguments();
        List<JsExpression> callArguments;
        if (arguments.isEmpty()) {
            callArguments = Collections.<JsExpression>singletonList(JsLiteral.THIS);
        }
        else {
            JsExpression[] callArgumentsArray = new JsExpression[arguments.size() + 1];
            callArgumentsArray[0] = JsLiteral.THIS;
            for (int i = 0; i < arguments.size(); i++) {
                JetExpression jetExpression = arguments.get(i).getArgumentExpression();
                assert jetExpression != null : "Argument with no expression";
                callArgumentsArray[i + 1] = Translation.translateAsExpression(jetExpression, context);
            }
            callArguments = Arrays.asList(callArgumentsArray);
        }

        JsInvocation call = new JsInvocation(new JsNameRef("call", context.getQualifiedReference(superClassDescriptor)), callArguments);
        constructor.getBody().getStatements().add(call);
    }

    public List<JsParameter> translateConstructorParameters(
            @NotNull TranslationContext context,
            @NotNull JsFunction initializer,
            @NotNull List<JsPropertyInitializer> properties,
            @NotNull TranslationContext declarationContext
    ) {
        ConstructorDescriptor primaryConstructor = descriptor.getUnsubstitutedPrimaryConstructor();
        List<ValueParameterDescriptor> parameters;
        List<JsNode> statements = initializer.getBody().getStatements();
        if (primaryConstructor == null) {
            parameters = null;
        }
        else {
            parameters = primaryConstructor.getValueParameters();
            FunctionTranslator.translateDefaultParametersInitialization(primaryConstructor, context, statements);
        }

        mayBeAddCallToSuperMethod(descriptor, declaration, initializer, context);

        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyList();
        }

        JsParameter[] result = new JsParameter[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            ValueParameterDescriptor parameter = parameters.get(i);
            JsParameter jsParameter = new JsParameter(context.getName(parameter));
            PropertyDescriptor propertyDescriptor = context.bindingContext().get(BindingContext.VALUE_PARAMETER_AS_PROPERTY, parameter);
            if (propertyDescriptor != null) {
                PropertyTranslator.translateAccessors(propertyDescriptor, properties, declarationContext);
                statements.add(assignmentToBackingField(context, propertyDescriptor, new JsNameRef(jsParameter.getName())));
            }
            result[i] = jsParameter;
        }
        return Arrays.asList(result);
    }
}