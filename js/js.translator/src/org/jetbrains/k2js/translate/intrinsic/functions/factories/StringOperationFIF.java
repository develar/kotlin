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

package org.jetbrains.k2js.translate.intrinsic.functions.factories;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.BuiltInFunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPattern;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.TranslationUtils;

import java.util.Arrays;
import java.util.List;

/**
 * @author Pavel Talanov
 */
public final class StringOperationFIF extends CompositeFIF {
    public StringOperationFIF(MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics) {
        super(intrinsics);

        DescriptorPattern string = new DescriptorPattern("jet", "String");
        add("get", string, new BuiltInFunctionIntrinsic("charAt"));
        add("toString", string, RETURN_RECEIVER_INTRINSIC);
        add("<get-length>", string, LENGTH_PROPERTY_INTRINSIC);

        DescriptorPattern kotlinWithReceiver = new DescriptorPattern("kotlin").receiverExists();
        add("length", kotlinWithReceiver, LENGTH_PROPERTY_INTRINSIC);

        add("startsWith", kotlinWithReceiver, new ContainsFunctionIntrinsic(false));
        add("contains", kotlinWithReceiver, new ContainsFunctionIntrinsic(true));
        add("endsWith", kotlinWithReceiver, new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                assert receiver != null;
                Pair<JsExpression, JsExpression> a = TranslationUtils.wrapAsTemporaryIfNeed(receiver, context);
                Pair<JsExpression, JsExpression> b = TranslationUtils.wrapAsTemporaryIfNeed(arguments.get(0), context);
                return JsAstUtils.inequality(new JsInvocation(new JsNameRef("indexOf", a.first), Arrays.asList(b.first, JsAstUtils.subtract(new JsNameRef("length", a.second), new JsNameRef("length", b.second)))), context.program().getNumberLiteral(-1));
            }
        });
        add("isEmpty", kotlinWithReceiver, IS_EMPTY_INTRINSIC);

        add("matches", kotlinWithReceiver, new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                assert receiver != null;
                return JsAstUtils.inequality(new JsInvocation(new JsNameRef("match", receiver), arguments), JsLiteral.NULL);
            }
        });
    }

    private static class ContainsFunctionIntrinsic extends FunctionIntrinsic {
        private final boolean contains;

        private ContainsFunctionIntrinsic(boolean contains) {
            this.contains = contains;
        }

        @NotNull
        @Override
        public JsExpression apply(
                @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
        ) {
            return new JsBinaryOperation(contains ? JsBinaryOperator.REF_NEQ : JsBinaryOperator.REF_EQ,
                                         new JsInvocation(new JsNameRef("indexOf", receiver), arguments),
                                         context.program().getNumberLiteral(contains ? -1 : 0));
        }
    }
}
