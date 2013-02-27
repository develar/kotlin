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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.jet.lang.types.lang.PrimitiveType;
import org.jetbrains.k2js.translate.context.Namer;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.BuiltInFunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPattern;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;

import java.util.List;

import static org.jetbrains.k2js.translate.utils.JsAstUtils.assignment;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.inequality;

public final class ArrayFIF extends CompositeFIF {
    @NotNull
    public static final FunctionIntrinsic GET_INTRINSIC = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(@Nullable JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context) {
            assert receiver != null;
            assert arguments.size() == 1 : "Array get expression must have one argument.";
            JsExpression indexExpression = arguments.get(0);
            return new JsArrayAccess(receiver, indexExpression);
        }
    };

    @NotNull
    public static final FunctionIntrinsic SET_INTRINSIC = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(@Nullable JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context) {
            assert receiver != null;
            assert arguments.size() == 2 : "Array set expression must have two arguments.";
            JsExpression indexExpression = arguments.get(0);
            JsExpression value = arguments.get(1);
            JsArrayAccess arrayAccess = new JsArrayAccess(receiver, indexExpression);
            return assignment(arrayAccess, value);
        }
    };

    public ArrayFIF(MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics) {
        super(intrinsics);

        FunctionIntrinsic arrayFromFun = kotlinFunction("arrayFromFun");
        FunctionIntrinsic arrayIndices = kotlinFunction("arrayIndices");
        FunctionIntrinsic iterator = kotlinFunction("arrayIterator");

        FunctionIntrinsic array = new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver,
                    @NotNull List<JsExpression> arguments,
                    @NotNull TranslationContext context
            ) {
                return new JsArrayLiteral(arguments);
            }
        };

        DescriptorPattern kotlin = new DescriptorPattern("kotlin");
        PrimitiveType[] primitiveTypes = PrimitiveType.values();
        for (int i = -1; i < primitiveTypes.length; i++) {
            String typeName;
            if (i == -1) {
                typeName = "Array";
                add("array", kotlin, array);
            }
            else {
                typeName = primitiveTypes[i].getArrayTypeName().getName();
                add(Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1) + "Array", kotlin, array);
            }
            DescriptorPattern namePattern = new DescriptorPattern("jet", typeName);
            add("<init>", namePattern, arrayFromFun);
            add("<get-indices>", namePattern, arrayIndices);
            add("<get-size>", namePattern, LENGTH_PROPERTY_INTRINSIC);
            add("get", namePattern, GET_INTRINSIC);
            add("set", namePattern, SET_INTRINSIC);
            add("iterator", namePattern, iterator);
        }

        add("<init>", new DescriptorPattern("java", "util", "ArrayList"), new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver,
                    @NotNull List<JsExpression> arguments,
                    @NotNull TranslationContext context
            ) {
                // arguments can contains capacity, but we ignore it
                return new JsArrayLiteral();
            }
        });

        DescriptorPattern list = new DescriptorPattern("jet", "List").checkOverridden();
        add("size", list, LENGTH_PROPERTY_INTRINSIC);
        add("get", list, kotlinFunction("arrayGet"));
        add("isEmpty", list, IS_EMPTY_INTRINSIC);
        add("iterator", list, iterator);
        add("indexOf", list, kotlinFunction("arrayIndexOf"));
        add("lastIndexOf", list, kotlinFunction("arrayLastIndexOf"));
        add("toArray", list, new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                assert receiver != null;
                return new JsInvocation(new JsNameRef("slice", receiver), context.program().getNumberLiteral(0));
            }
        });
        add("contains", list, new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                assert receiver != null && arguments.size() == 1;
                return inequality(new JsInvocation(Namer.kotlin("arrayIndexOf"), receiver, arguments.get(0)),
                                  context.program().getNumberLiteral(-1));
            }
        });

        add("equals", list, kotlinFunction("arrayEquals"));
        add("toString", list, kotlinFunction("arrayToString"));

        DescriptorPattern mutableList = new DescriptorPattern("jet", "MutableList").checkOverridden();
        add("set", mutableList, kotlinFunction("arraySet"));
        // http://jsperf.com/push-vs-len
        add("add", new ValueParametersAwareDescriptorPredicate(mutableList, 1), new BuiltInFunctionIntrinsic("push"));
        add("add", new ValueParametersAwareDescriptorPredicate(mutableList, 2), kotlinFunction("arrayAddAt"));
        add("addAll", mutableList , kotlinFunction("arrayAddAll"));
        add("remove", new ValueParametersAwareDescriptorPredicate(mutableList, Predicates.equalTo(KotlinBuiltIns.getInstance().getIntType())),
            kotlinFunction("arrayRemoveAt"));
        add("remove", new ValueParametersAwareDescriptorPredicate(mutableList, Predicates.equalTo(KotlinBuiltIns.getInstance().getNullableAnyType())),
            kotlinFunction("arrayRemove"));
        add("clear", mutableList , new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                assert receiver != null;
                return assignment(new JsNameRef("length", receiver), context.program().getNumberLiteral(0));
            }
        });

        add("iterator", new DescriptorPattern("jet", "Iterable").checkOverridden(), kotlinFunction("collectionIterator"));

        DescriptorPattern collection = new DescriptorPattern("jet", "Collection").checkOverridden();
        add("size", collection, kotlinFunction("collectionSize"));
        add("isEmpty", collection, kotlinFunction("collectionIsEmpty"));
        add("contains", collection, kotlinFunction("collectionContains"));

        DescriptorPattern mutableCollection = new DescriptorPattern("jet", "MutableCollection").checkOverridden();
        add("add", mutableCollection, kotlinFunction("collectionAdd"));
        add("addAll", mutableCollection, kotlinFunction("collectionAddAll"));
        add("remove", mutableCollection, kotlinFunction("collectionRemove"));
        add("clear", mutableCollection, kotlinFunction("collectionClear"));
    }

    private final static class ValueParametersAwareDescriptorPredicate implements DescriptorPredicate {
        private final DescriptorPredicate predicate;
        private final int parameterCount;
        private final Predicate<JetType> firstParameterPredicate;

        public ValueParametersAwareDescriptorPredicate(@NotNull DescriptorPredicate predicate, @NotNull Predicate<JetType> firstParameterPredicate) {
            this.predicate = predicate;
            this.firstParameterPredicate = firstParameterPredicate;
            parameterCount = -1;
        }

        public ValueParametersAwareDescriptorPredicate(@NotNull DescriptorPredicate predicate, int parameterCount) {
            this.predicate = predicate;
            this.parameterCount = parameterCount;
            firstParameterPredicate = null;
        }

        @Override
        public boolean apply(@NotNull FunctionDescriptor descriptor) {
            if (!predicate.apply(descriptor)) {
                return false;
            }

            List<ValueParameterDescriptor> valueParameters = descriptor.getValueParameters();
            if (firstParameterPredicate == null) {
                assert parameterCount != -1;
                return valueParameters.size() == parameterCount;
            }
            else {
                return valueParameters.size() == 1 && firstParameterPredicate.apply(valueParameters.get(0).getType());
            }
        }
    }
}
