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
import com.google.dart.compiler.util.AstUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.k2js.translate.context.TemporaryVariable;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPattern;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;

import java.util.List;

import static org.jetbrains.jet.lang.types.expressions.OperatorConventions.*;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.assignment;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.subtract;

public final class NumberConversionFIF extends CompositeFIF {
    @NotNull
    private static final FunctionIntrinsic RETURN_RECEIVER = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(@Nullable JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context) {
            assert receiver != null;
            assert arguments.isEmpty();
            return receiver;
        }
    };

    @NotNull
    private static final FunctionIntrinsic INTEGER_DIVISION_INTRINSIC = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(@Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context) {
            assert receiver != null;
            TemporaryVariable left = context.declareTemporary(receiver);
            assert arguments.size() == 1;
            TemporaryVariable right = context.declareTemporary(arguments.get(0));
            JsBinaryOperation divRes = new JsBinaryOperation(JsBinaryOperator.DIV, left.reference(), right.reference());
            JsBinaryOperation modRes = new JsBinaryOperation(JsBinaryOperator.MOD, left.reference(), right.reference());
            JsBinaryOperation fractionalPart = new JsBinaryOperation(JsBinaryOperator.DIV, modRes, right.reference());
            return AstUtil.newSequence(left.assignmentExpression(), right.assignmentExpression(), subtract(divRes, fractionalPart));
        }
    };

    @NotNull
    private static final FunctionIntrinsic GET_INTEGER_PART = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(@Nullable JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context) {
            assert receiver != null;
            assert arguments.isEmpty();
            JsNameRef toConvertReference = context.declareTemporary(null).reference();
            JsBinaryOperation fractional =
                    new JsBinaryOperation(JsBinaryOperator.MOD, toConvertReference, new JsNumberLiteral(1));
            return subtract(assignment(toConvertReference, receiver), fractional);
        }
    };

    public NumberConversionFIF(MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics) {
        super(intrinsics);

        for (String typeName : new String[] {"Int", "Byte", "Short"}) {
            DescriptorPattern packagePattern = new DescriptorPattern("jet", typeName);
            for (Name conversionMethodName : NUMBER_CONVERSIONS) {
                add(conversionMethodName.asString(), packagePattern, RETURN_RECEIVER);
            }
            add(CHAR.asString(), packagePattern, RETURN_RECEIVER);
            add(LONG.asString(), packagePattern, RETURN_RECEIVER);

            add("div", packagePattern, INTEGER_DIVISION_INTRINSIC);
        }

        Name[] integerConversions = {INT, SHORT, BYTE};
        for (String typeName : new String[] {"Float", "Double", "Number"}) {
            DescriptorPattern packagePattern = new DescriptorPattern("jet", typeName);

            for (Name conversionMethodName : integerConversions) {
                add(conversionMethodName.asString(), packagePattern, GET_INTEGER_PART);
            }

            add(FLOAT.asString(), packagePattern, RETURN_RECEIVER);
            add(DOUBLE.asString(), packagePattern, RETURN_RECEIVER);
        }
    }
}
