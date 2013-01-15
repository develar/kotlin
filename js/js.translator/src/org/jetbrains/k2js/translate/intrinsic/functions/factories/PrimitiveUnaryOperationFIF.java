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

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsPrefixOperation;
import com.google.dart.compiler.backend.js.ast.JsUnaryOperator;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.expressions.OperatorConventions;
import org.jetbrains.jet.lang.types.lang.PrimitiveType;
import org.jetbrains.jet.lexer.JetToken;
import org.jetbrains.jet.lexer.JetTokens;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPattern;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;
import org.jetbrains.k2js.translate.operation.OperatorTable;

import java.util.List;

/**
 * @author Pavel Talanov
 */
public class PrimitiveUnaryOperationFIF extends CompositeFIF {
    public PrimitiveUnaryOperationFIF(MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics) {
        super(intrinsics);

        for (PrimitiveType numberType : PrimitiveType.NUMBER_TYPES) {
            DescriptorPredicate predicate = new MyDescriptorPredicate(new DescriptorPattern("jet", numberType.getTypeName().getName()));
            for (Name name : OperatorConventions.UNARY_OPERATION_NAMES.values()) {
                JetToken jetToken = OperatorConventions.UNARY_OPERATION_NAMES.inverse().get(name);
                add(name.getName(), predicate, new MyFunctionIntrinsic(OperatorTable.getUnaryOperator(jetToken)));
            }
        }
        add("not", new MyDescriptorPredicate(new DescriptorPattern("jet", "Boolean")),
            new MyFunctionIntrinsic(OperatorTable.getUnaryOperator(JetTokens.EXCL)));
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

    private static class MyDescriptorPredicate implements DescriptorPredicate {
        private final DescriptorPattern pattern;

        private MyDescriptorPredicate(DescriptorPattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean apply(@NotNull FunctionDescriptor descriptor) {
            return descriptor.getValueParameters().isEmpty() && pattern.apply(descriptor);
        }
    }
}
