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
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.expressions.OperatorConventions;
import org.jetbrains.jet.lang.types.lang.PrimitiveType;
import org.jetbrains.jet.lexer.JetToken;
import org.jetbrains.jet.lexer.JetTokens;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPattern;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;
import org.jetbrains.k2js.translate.operation.OperatorTable;
import org.jetbrains.k2js.translate.utils.JsDescriptorUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jetbrains.k2js.translate.utils.JsAstUtils.subtract;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.sum;

public class PrimitiveUnaryOperationFIF extends CompositeFIF {
    private static final FunctionIntrinsic RANGE_TO_INTRINSIC = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(@Nullable JsExpression rangeStart, @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context) {
            assert arguments.size() == 1 : "RangeTo must have one argument.";
            assert rangeStart != null;
            JsExpression rangeEnd = arguments.get(0);
            JsBinaryOperation rangeSize = sum(subtract(rangeEnd, rangeStart), context.program().getNumberLiteral(1));
            JsExpression nameRef = Namer.kotlin("NumberRange");
            //TODO: add tests and correct expression for reversed ranges.
            List<JsExpression> args = Arrays.asList(rangeStart, rangeSize, /*range is not reversed*/JsLiteral.FALSE);
            return context.isEcma5() ? new JsInvocation(nameRef, args) : new JsNew(nameRef, args);
        }
    };

    public PrimitiveUnaryOperationFIF(MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics) {
        super(intrinsics);

        DescriptorPredicate numberPredicate = new DescriptorPredicate() {
            @Override
            public boolean apply(@NotNull FunctionDescriptor descriptor) {
                DeclarationDescriptor classDescriptor = descriptor.getContainingDeclaration();
                if (!JsDescriptorUtils.isInBuiltInsPackage(classDescriptor)) {
                    return false;
                }
                for (PrimitiveType type : PrimitiveType.NUMBER_TYPES) {
                    if (type.getTypeName().equals(classDescriptor.getName())) {
                        return true;
                    }
                }
                return false;
            }
        };

        DescriptorPredicate predicate = new MyDescriptorPredicate(numberPredicate);
        for (Map.Entry<JetToken, Name> entry : OperatorConventions.UNARY_OPERATION_NAMES.entrySet()) {
            add(entry.getValue().getName(), predicate, new MyFunctionIntrinsic(OperatorTable.getUnaryOperator(entry.getKey())));
        }

        for (Map.Entry<JetToken, Name> entry : OperatorConventions.BINARY_OPERATION_NAMES.entrySet()) {
            JetToken token = entry.getKey();
            JsBinaryOperator operator = OperatorTable.getNullableBinaryOperator(token);
            FunctionIntrinsic intrinsic;
            if (operator != null) {
                intrinsic = new PrimitiveBinaryOperationFunctionIntrinsic(operator);
            }
            else if (token == JetTokens.RANGE) {
                intrinsic = RANGE_TO_INTRINSIC;
            }
            else {
                continue;
            }

            add(entry.getValue().getName(), numberPredicate, intrinsic);
        }

        DescriptorPattern booleanPattern = new DescriptorPattern("jet", "Boolean");
        add("or", booleanPattern, new PrimitiveBinaryOperationFunctionIntrinsic(JsBinaryOperator.OR));
        add("and", booleanPattern, new PrimitiveBinaryOperationFunctionIntrinsic(JsBinaryOperator.AND));
        add("xor", booleanPattern, new PrimitiveBinaryOperationFunctionIntrinsic(JsBinaryOperator.BIT_XOR));

        add("not", new MyDescriptorPredicate(booleanPattern), new MyFunctionIntrinsic(JsUnaryOperator.NOT));

        add("plus", new DescriptorPattern("jet", "String"), new PrimitiveBinaryOperationFunctionIntrinsic(JsBinaryOperator.ADD));
    }

    private static class PrimitiveBinaryOperationFunctionIntrinsic extends FunctionIntrinsic {
        @NotNull
        private final JsBinaryOperator operator;

        private PrimitiveBinaryOperationFunctionIntrinsic(@NotNull JsBinaryOperator operator) {
            this.operator = operator;
        }

        @NotNull
        @Override
        public JsExpression apply(
                @Nullable JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context
        ) {
            assert receiver != null;
            assert arguments.size() == 1 : "Binary operator should have a receiver and one argument";
            return new JsBinaryOperation(operator, receiver, arguments.get(0));
        }
    }

    private static class MyFunctionIntrinsic extends FunctionIntrinsic {
        private final JsUnaryOperator jsOperator;

        public MyFunctionIntrinsic(JsUnaryOperator jsOperator) {
            this.jsOperator = jsOperator;
        }

        @NotNull
        @Override
        public JsExpression apply(
                @Nullable JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context
        ) {
            assert receiver != null;
            return new JsPrefixOperation(jsOperator, receiver);
        }
    }

    // todo eliminate this class
    private static class MyDescriptorPredicate implements DescriptorPredicate {
        private final DescriptorPredicate predicate;

        private MyDescriptorPredicate(DescriptorPredicate pattern) {
            this.predicate = pattern;
        }

        @Override
        public boolean apply(@NotNull FunctionDescriptor descriptor) {
            return descriptor.getValueParameters().isEmpty() && predicate.apply(descriptor);
        }
    }
}
