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

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.backend.js.ast.JsNumberLiteral;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.BuiltInPropertyIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.List;

public abstract class CompositeFIF {
    @NotNull
    public static final FunctionIntrinsic LENGTH_PROPERTY_INTRINSIC = new BuiltInPropertyIntrinsic("length");
    public static final FunctionIntrinsic IS_EMPTY_INTRINSIC = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(
                @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
        ) {
            assert receiver != null;
            return JsAstUtils.equality(new JsNameRef("length", receiver), JsNumberLiteral.V_0);
        }
    };
    @NotNull
    protected static final FunctionIntrinsic RETURN_RECEIVER_INTRINSIC = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(
                @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
        ) {
            assert receiver != null;
            return receiver;
        }
    };

    private final MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics;

    protected CompositeFIF(MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics) {
        this.intrinsics = intrinsics;
    }

    protected void add(@NotNull String memberName, @NotNull DescriptorPredicate packageNamePattern, @NotNull FunctionIntrinsic intrinsic) {
        intrinsics.add(memberName, Pair.create(packageNamePattern, intrinsic));
    }

    protected static FunctionIntrinsic kotlinFunction(@NotNull String functionName) {
        return new QualifiedInvocationFunctionIntrinsic(functionName, Namer.KOTLIN_OBJECT_NAME_REF);
    }
}
