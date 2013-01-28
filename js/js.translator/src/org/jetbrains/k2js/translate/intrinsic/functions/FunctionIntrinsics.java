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

package org.jetbrains.k2js.translate.intrinsic.functions;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Processor;
import com.intellij.util.containers.MostlySingularMultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.factories.*;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;

import java.util.Map;

public final class FunctionIntrinsics {
    public static final String ANY_MEMBER = "";

    // member name -> descriptor predicate : intrinsic
    private static final MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics = new MostlySingularMultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>>();
    private static final Iterable<Pair<DescriptorPredicate, FunctionIntrinsic>> anyIntrinsics;

    static {
        new ArrayFIF(intrinsics);
        new TopLevelFIF(intrinsics);
        new StringOperationFIF(intrinsics);
        new NumberConversionFIF(intrinsics);
        new PrimitiveUnaryOperationFIF(intrinsics);
        anyIntrinsics = intrinsics.get(ANY_MEMBER);
    }

    @NotNull
    private final Map<FunctionDescriptor, FunctionIntrinsic> intrinsicCache = new THashMap<FunctionDescriptor, FunctionIntrinsic>();

    @Nullable
    public FunctionIntrinsic getIntrinsic(@NotNull FunctionDescriptor descriptor) {
        FunctionIntrinsic intrinsic = intrinsicCache.get(descriptor);
        if (intrinsic == null) {
            if (intrinsics.valuesForKey(descriptor.getName().getName()) == 0) {
                return null;
            }
            intrinsic = computeIntrinsic(descriptor);
            intrinsicCache.put(descriptor, intrinsic);
        }
        return intrinsic.exists() ? intrinsic : null;
    }

    @NotNull
    private static FunctionIntrinsic computeIntrinsic(@NotNull FunctionDescriptor descriptor) {
        MyProcessor processor = new MyProcessor(descriptor);
        intrinsics.processForKey(descriptor.getName().getName(), processor);
        if (processor.result != null) {
            return processor.result;
        }

        if (anyIntrinsics != null) {
            for (Pair<DescriptorPredicate, FunctionIntrinsic> pair : anyIntrinsics) {
                if (pair.first.apply(descriptor)) {
                    return pair.second;
                }
            }
        }

        return FunctionIntrinsic.NO_INTRINSIC;
    }

    private static final class MyProcessor implements Processor<Pair<DescriptorPredicate, FunctionIntrinsic>> {
        private final FunctionDescriptor descriptor;
        private FunctionIntrinsic result;

        public MyProcessor(FunctionDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public boolean process(Pair<DescriptorPredicate, FunctionIntrinsic> pair) {
            if (pair.first.apply(descriptor)) {
                result = pair.second;
                return false;
            }
            return true;
        }
    }
}