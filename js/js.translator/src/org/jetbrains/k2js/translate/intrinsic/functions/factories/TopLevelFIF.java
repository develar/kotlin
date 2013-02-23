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

package org.jetbrains.k2js.translate.intrinsic.functions.factories;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetQualifiedExpression;
import org.jetbrains.jet.lang.psi.JetReferenceExpression;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.calls.model.ResolvedCall;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.resolve.scopes.receivers.ExpressionReceiver;
import org.jetbrains.jet.lang.resolve.scopes.receivers.ReceiverValue;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.jet.lang.types.lang.PrimitiveType;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.FunctionIntrinsics;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPattern;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.NamePredicate;
import org.jetbrains.k2js.translate.reference.CallTranslator;
import org.jetbrains.k2js.translate.utils.BindingUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.JsDescriptorUtils;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.TranslationUtils.generateInvocationArguments;

public final class TopLevelFIF extends CompositeFIF {
    @NotNull
    public static final FunctionIntrinsic EQUALS = kotlinFunction("equals");

    private static final FunctionIntrinsic NATIVE_MAP_GET = new NativeMapGetSet() {
        @NotNull
        @Override
        protected String operation() {
            return "get";
        }

        @Nullable
        @Override
        protected ExpressionReceiver getExpressionReceiver(@NotNull ResolvedCall<?> resolvedCall) {
            ReceiverValue result = resolvedCall.getThisObject();
            return result instanceof ExpressionReceiver ? (ExpressionReceiver) result : null;
        }

        @Override
        protected JsExpression asArrayAccess(
                @NotNull JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context
        ) {
            return ArrayFIF.GET_INTRINSIC.apply(receiver, arguments, context);
        }
    };

    private static final FunctionIntrinsic NATIVE_MAP_SET = new NativeMapGetSet() {
        @NotNull
        @Override
        protected String operation() {
            return "put";
        }

        @Nullable
        @Override
        protected ExpressionReceiver getExpressionReceiver(@NotNull ResolvedCall<?> resolvedCall) {
            ReceiverValue result = resolvedCall.getReceiverArgument();
            return result instanceof ExpressionReceiver ? (ExpressionReceiver) result : null;
        }

        @Override
        protected JsExpression asArrayAccess(
                @NotNull JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context
        ) {
            return ArrayFIF.SET_INTRINSIC.apply(receiver, arguments, context);
        }
    };

    public static final FunctionIntrinsic STRINGIFY = kotlinFunction("stringify");

    public static final DescriptorPredicate JAVA_EXCEPTION_PATTERN = new DescriptorPredicate() {
        @Override
        public boolean apply(@NotNull FunctionDescriptor descriptor) {
            if (!(descriptor instanceof ConstructorDescriptor)) {
                return false;
            }

            DeclarationDescriptor packageDescriptor =
                    ((ConstructorDescriptor) descriptor).getContainingDeclaration().getContainingDeclaration();
            if (packageDescriptor.getName().getName().equals("lang")) {
                DeclarationDescriptor javaPackage = packageDescriptor.getContainingDeclaration();
                if (javaPackage != null && javaPackage.getName().getName().equals("java")) {
                    DeclarationDescriptor root = javaPackage.getContainingDeclaration();
                    return root instanceof NamespaceDescriptor && DescriptorUtils.isRootNamespace((NamespaceDescriptor) root);
                }
            }

            return false;
        }
    };

    public TopLevelFIF(MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics) {
        super(intrinsics);

        DescriptorPattern jet = new DescriptorPattern("jet");
        DescriptorPattern jetWithReceiver = new DescriptorPattern("jet").receiverExists();
        add("toString", jetWithReceiver, STRINGIFY);
        add("equals", jetWithReceiver, EQUALS);
        add("iterator", jetWithReceiver, RETURN_RECEIVER_INTRINSIC);

        for (PrimitiveType primitiveType : PrimitiveType.values()) {
            add("equals", new DescriptorPattern("jet", primitiveType.getTypeName().getName()), EQUALS);
        }
        add("equals", new DescriptorPattern("jet", "String"), EQUALS);
        add("equals", new DescriptorPattern("jet", "Hashable"), EQUALS);

        add("arrayOfNulls", jet, kotlinFunction("arrayOfNulls"));

        add("invoke", new DescriptorPredicate() {
                @Override
                public boolean apply(@NotNull FunctionDescriptor descriptor) {
                    int parameterCount = descriptor.getValueParameters().size();
                    DeclarationDescriptor fun = descriptor.getContainingDeclaration();
                    return fun == (descriptor.getReceiverParameter() == null
                                   ? KotlinBuiltIns.getInstance().getFunction(parameterCount)
                                   : KotlinBuiltIns.getInstance().getExtensionFunction(parameterCount));
                }
            }, new FunctionIntrinsic() {
                @NotNull
                @Override
                public JsExpression apply(
                        @NotNull CallTranslator callTranslator,
                        @NotNull List<JsExpression> arguments,
                        @NotNull TranslationContext context
                ) {
                    JsExpression thisExpression = callTranslator.getParameters().getThisObject();
                    JsExpression functionReference = callTranslator.getParameters().getFunctionReference();
                    if (thisExpression == null) {
                        return new JsInvocation(functionReference, arguments);
                    }
                    else if (callTranslator.getResolvedCall().getReceiverArgument().exists()) {
                        return callTranslator.extensionFunctionCall(false);
                    }
                    else {
                        if (functionReference instanceof JsNameRef && ((JsNameRef) functionReference).getName().equals("invoke")) {
                            return callTranslator.explicitInvokeCall();
                        }
                        return new JsInvocation(new JsNameRef("call", functionReference),
                                                generateInvocationArguments(thisExpression, arguments));
                    }
                }
            }
        );

        add("get", new DescriptorPattern("jet", "Map").checkOverridden(), NATIVE_MAP_GET);
        DescriptorPattern js = new DescriptorPattern("js");
        add("set", new DescriptorPattern("js").receiverExists(), NATIVE_MAP_SET);

        DescriptorPattern json = new DescriptorPattern("js", "Json");
        add("get", json, ArrayFIF.GET_INTRINSIC);
        add("set", json, ArrayFIF.SET_INTRINSIC);

        JsNameRef systemOut = new JsNameRef("out", new JsNameRef("System", Namer.KOTLIN_OBJECT_NAME_REF));
        add("println", js, new QualifiedInvocationFunctionIntrinsic("println", systemOut));
        add("print", js, new QualifiedInvocationFunctionIntrinsic("print", systemOut));

        add("<init>", new DescriptorPattern("java", "util", "HashMap"), new MapSelectImplementationIntrinsic(false));
        add("<init>", new DescriptorPattern("java", "util", "HashSet"), new MapSelectImplementationIntrinsic(true));
        DescriptorPattern stringBuilder = new DescriptorPattern("java", "util", "StringBuilder");
        add("<init>", stringBuilder, new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                return JsAstUtils.wrapValue("s", new JsStringLiteral(""));
            }
        });
        add("append", new DescriptorPattern("java", "lang", "Appendable").checkOverridden(), new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                assert arguments.size() == 1;
                return new JsBinaryOperation(JsBinaryOperator.ASG_ADD, new JsNameRef("s", receiver), arguments.get(0));
            }
        });
        add("toString", stringBuilder, new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                return new JsNameRef("s", receiver);
            }
        });

        add(FunctionIntrinsics.ANY_MEMBER, JAVA_EXCEPTION_PATTERN, new FunctionIntrinsic() {
                @NotNull
                @Override
                public JsExpression apply(
                        @NotNull CallTranslator callTranslator,
                        @NotNull List<JsExpression> arguments,
                        @NotNull TranslationContext context
                ) {
                    JsStringLiteral exceptionName = new JsStringLiteral(
                            callTranslator.getParameters().getDescriptor().getContainingDeclaration().getName().getName());
                    return new JsInvocation(Namer.NEW_EXCEPTION_FUN_NAME_REF,
                                            Arrays.asList(arguments.size() == 1 ? arguments.get(0) : JsLiteral.NULL, exceptionName));
                }
            }
        );
    }

    private abstract static class NativeMapGetSet extends FunctionIntrinsic {
        @NotNull
        protected abstract String operation();

        @Nullable
        protected abstract ExpressionReceiver getExpressionReceiver(@NotNull ResolvedCall<?> resolvedCall);

        protected abstract JsExpression asArrayAccess(
                @NotNull JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context
        );

        @NotNull
        @Override
        public JsExpression apply(@NotNull CallTranslator callTranslator, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context) {
            ExpressionReceiver expressionReceiver = getExpressionReceiver(callTranslator.getResolvedCall());
            JsExpression thisOrReceiver = callTranslator.getParameters().getThisOrReceiverOrNull();
            assert thisOrReceiver != null;
            if (expressionReceiver != null) {
                JetExpression expression = expressionReceiver.getExpression();
                JetReferenceExpression referenceExpression = null;
                if (expression instanceof JetReferenceExpression) {
                    referenceExpression = (JetReferenceExpression) expression;
                }
                else if (expression instanceof JetQualifiedExpression) {
                    JetExpression candidate = ((JetQualifiedExpression) expression).getReceiverExpression();
                    if (candidate instanceof JetReferenceExpression) {
                        referenceExpression = (JetReferenceExpression) candidate;
                    }
                }

                if (referenceExpression != null) {
                    DeclarationDescriptor candidate = BindingUtils.getDescriptorForReferenceExpression(context.bindingContext(),
                                                                                                       referenceExpression);
                    if (candidate instanceof PropertyDescriptor && context.isNative(candidate)) {
                        return asArrayAccess(thisOrReceiver, arguments, context);
                    }
                }
            }

            return new JsInvocation(new JsNameRef(operation(), thisOrReceiver), arguments);
        }
    }

    private static class MapSelectImplementationIntrinsic extends FunctionIntrinsic {
        private final boolean isSet;

        private MapSelectImplementationIntrinsic(boolean isSet) {
            this.isSet = isSet;
        }

        @NotNull
        @Override
        public JsExpression apply(
                @NotNull CallTranslator callTranslator,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context
        ) {
            JetType keyType = callTranslator.getResolvedCall().getTypeArguments().values().iterator().next();
            Name keyTypeName = JsDescriptorUtils.getNameIfStandardType(keyType);
            String collectionClassName;
            if (keyTypeName != null &&
                (NamePredicate.PRIMITIVE_NUMBERS.apply(keyTypeName) ||
                 keyTypeName.getName().equals("String") ||
                 PrimitiveType.BOOLEAN.getTypeName().equals(keyTypeName))) {
                collectionClassName = isSet ? "PrimitiveHashSet" : "PrimitiveHashMap";
            }
            else {
                collectionClassName = isSet ? "ComplexHashSet" : "ComplexHashMap";
            }

            return callTranslator.createConstructorCallExpression(Namer.kotlin(collectionClassName));
        }
    }
}
