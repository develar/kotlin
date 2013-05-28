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

package org.jetbrains.k2js.translate.utils;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JsAstUtils {
    private static final JsNameRef DEFINE_PROPERTY = new JsNameRef("defineProperty");
    public static final JsNameRef CREATE_OBJECT = new JsNameRef("create");

    private static final JsNameRef VALUE = new JsNameRef("value");
    private static final JsPropertyInitializer WRITABLE = new JsPropertyInitializer(new JsNameRef("writable"), JsLiteral.TRUE);
    private static final JsPropertyInitializer ENUMERABLE = new JsPropertyInitializer(new JsNameRef("enumerable"), JsLiteral.TRUE);

    public static final String LENDS_JS_DOC_TAG = "lends";

    static {
        JsNameRef globalObjectReference = new JsNameRef("Object");
        DEFINE_PROPERTY.setQualifier(globalObjectReference);
        CREATE_OBJECT.setQualifier(globalObjectReference);
    }

    private JsAstUtils() {
    }

    @NotNull
    public static JsBlock convertToBlock(@NotNull JsNode jsNode) {
        if (jsNode instanceof JsBlock) {
            return (JsBlock) jsNode;
        }
        return new JsBlock(jsNode.asStatement());
    }

    @NotNull
    public static JsExpression convertToExpression(@NotNull JsNode jsNode) {
        assert jsNode instanceof JsExpression : "Unexpected node of type: " + jsNode.getClass().toString();
        return (JsExpression) jsNode;
    }

    @NotNull
    public static JsPrefixOperation negated(@NotNull JsExpression expression) {
        return new JsPrefixOperation(JsUnaryOperator.NOT, expression);
    }

    @NotNull
    public static JsBinaryOperation and(@NotNull JsExpression op1, @NotNull JsExpression op2) {
        return new JsBinaryOperation(JsBinaryOperator.AND, op1, op2);
    }

    @NotNull
    public static JsBinaryOperation or(@NotNull JsExpression op1, @NotNull JsExpression op2) {
        return new JsBinaryOperation(JsBinaryOperator.OR, op1, op2);
    }

    public static void setQualifier(@NotNull JsExpression selector, @Nullable JsExpression receiver) {
        assert (selector instanceof JsInvocation || selector instanceof JsNameRef);
        if (selector instanceof JsInvocation) {
            setQualifier(((JsInvocation) selector).getQualifier(), receiver);
            return;
        }
        setQualifierForNameRef((JsNameRef) selector, receiver);
    }

    private static void setQualifierForNameRef(@NotNull JsNameRef selector, @Nullable JsExpression receiver) {
        JsExpression qualifier = selector.getQualifier();
        if (qualifier == null) {
            selector.setQualifier(receiver);
        }
        else {
            setQualifier(qualifier, receiver);
        }
    }

    @NotNull
    public static JsBinaryOperation equality(@NotNull JsExpression arg1, @NotNull JsExpression arg2) {
        return new JsBinaryOperation(JsBinaryOperator.REF_EQ, arg1, arg2);
    }

    @NotNull
    public static JsBinaryOperation inequality(@NotNull JsExpression arg1, @NotNull JsExpression arg2) {
        return new JsBinaryOperation(JsBinaryOperator.REF_NEQ, arg1, arg2);
    }

    @NotNull
    public static JsBinaryOperation lessThanEq(@NotNull JsExpression arg1, @NotNull JsExpression arg2) {
        return new JsBinaryOperation(JsBinaryOperator.LTE, arg1, arg2);
    }

    @NotNull
    public static JsExpression assignment(@NotNull JsExpression left, @NotNull JsExpression right) {
        return new JsBinaryOperation(JsBinaryOperator.ASG, left, right);
    }

    @NotNull
    public static JsBinaryOperation sum(@NotNull JsExpression left, @NotNull JsExpression right) {
        return new JsBinaryOperation(JsBinaryOperator.ADD, left, right);
    }

    @NotNull
    public static JsBinaryOperation addAssign(@NotNull JsExpression left, @NotNull JsExpression right) {
        return new JsBinaryOperation(JsBinaryOperator.ASG_ADD, left, right);
    }

    @NotNull
    public static JsBinaryOperation subtract(@NotNull JsExpression left, @NotNull JsExpression right) {
        return new JsBinaryOperation(JsBinaryOperator.SUB, left, right);
    }

    @NotNull
    public static JsPrefixOperation not(@NotNull JsExpression expression) {
        return new JsPrefixOperation(JsUnaryOperator.NOT, expression);
    }

    @NotNull
    public static JsBinaryOperation typeof(@NotNull JsExpression expression, @NotNull JsStringLiteral string) {
        return equality(new JsPrefixOperation(JsUnaryOperator.TYPEOF, expression), string);
    }

    @NotNull
    public static JsVars newVar(@NotNull String name, @Nullable JsExpression expr) {
        return new JsVars(new JsVars.JsVar(name, expr));
    }

    @NotNull
    public static JsExpression newSequence(@NotNull List<JsExpression> expressions) {
        assert !expressions.isEmpty();
        if (expressions.size() == 1) {
            return expressions.get(0);
        }
        JsExpression result = expressions.get(expressions.size() - 1);
        for (int i = expressions.size() - 2; i >= 0; i--) {
            result = new JsBinaryOperation(JsBinaryOperator.COMMA, expressions.get(i), result);
        }
        return result;
    }

    @NotNull
    public static JsFunction createFunctionWithEmptyBody(@NotNull JsScope parent) {
        return new JsFunction(parent, new JsBlock());
    }

    @NotNull
    public static <T> List<T> newList(int size) {
        return size == 1 ? new SmartList<T>() : new ArrayList<T>(size);
    }

    @NotNull
    public static List<JsExpression> toStringLiteralList(@NotNull List<String> strings) {
        if (strings.isEmpty()) {
            return Collections.emptyList();
        }

        List<JsExpression> result = newList(strings.size());
        for (String s : strings) {
            result.add(new JsStringLiteral(s));
        }
        return result;
    }

    public static String createNameForProperty(@NotNull PropertyDescriptor descriptor, @NotNull TranslationContext context) {
        return createNameForProperty(descriptor, context.isEcma5());
    }

    public static String createNameForProperty(@NotNull PropertyDescriptor descriptor, boolean isEcma5) {
        if (isEcma5 && !JsDescriptorUtils.isAsPrivate(descriptor)) {
            return descriptor.getName().asString();
        }
        else {
            return Namer.getKotlinBackingFieldName(descriptor.getName().asString());
        }
    }

    @NotNull
    public static JsInvocation definePropertyDataDescriptor(@NotNull PropertyDescriptor descriptor,
            @NotNull JsExpression value,
            @NotNull TranslationContext context) {
        return defineProperty(createNameForProperty(descriptor, context), createPropertyDataDescriptor(descriptor, value)
        );
    }

    @NotNull
    public static JsInvocation defineProperty(@NotNull String name, @NotNull JsObjectLiteral value) {
        return new JsInvocation(DEFINE_PROPERTY, JsLiteral.THIS, new JsStringLiteral(name), value);
    }

    @NotNull
    public static JsObjectLiteral createDataDescriptor(@NotNull JsExpression value) {
        return createDataDescriptor(value, false, false);
    }

    @NotNull
    public static JsObjectLiteral createDataDescriptor(@NotNull JsExpression value, boolean writable, boolean enumerable) {
        JsObjectLiteral dataDescriptor = new JsObjectLiteral();
        dataDescriptor.getPropertyInitializers().add(new JsPropertyInitializer(VALUE, value));
        if (writable) {
            dataDescriptor.getPropertyInitializers().add(WRITABLE);
        }
        if (enumerable) {
            dataDescriptor.getPropertyInitializers().add(ENUMERABLE);
        }
        return dataDescriptor;
    }

    @NotNull
    public static JsObjectLiteral createPropertyDataDescriptor(@NotNull PropertyDescriptor descriptor, @NotNull JsExpression value) {
        return createPropertyDataDescriptor(descriptor, descriptor.isVar(), value);
    }

    @NotNull
    public static JsObjectLiteral createPropertyDataDescriptor(
            @NotNull DeclarationDescriptor descriptor,
            boolean writable,
            @NotNull JsExpression value
    ) {
        return createDataDescriptor(value, writable, JsAnnotations.isEnumerable(descriptor));
    }

    @NotNull
    public static JsObjectLiteral wrapValue(@NotNull String label, @NotNull JsExpression value) {
        return wrapValue(new JsNameRef(label), value);
    }

    @NotNull
    public static JsObjectLiteral wrapValue(@NotNull JsExpression label, @NotNull JsExpression value) {
        return new JsObjectLiteral(Collections.singletonList(new JsPropertyInitializer(label, value)));
    }
}
