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

import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.factories.*;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class FunctionIntrinsics {
    public static final String ANY_MEMBER = "";

    // member name -> descriptor name predicate : intrinsic
    private static final MultiMap<String, Pair<DescriptorPredicate, FunctionIntrinsic>> intrinsics = MultiMap.createSmartList();
    private static final Collection<Pair<DescriptorPredicate, FunctionIntrinsic>> anyIntrinsics;

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

    @NotNull
    private final List<FunctionIntrinsicFactory> factories = Lists.newArrayList();

    public FunctionIntrinsics() {
        registerFactories();
    }

    private void registerFactories() {
        register(PrimitiveBinaryOperationFIF.INSTANCE);
    }

    private void register(@NotNull FunctionIntrinsicFactory instance) {
        factories.add(instance);
    }

    @NotNull
    public FunctionIntrinsic getIntrinsic(@NotNull FunctionDescriptor descriptor) {
        FunctionIntrinsic intrinsic = lookUpCache(descriptor);
        return intrinsic != null ? intrinsic : computeAndCacheIntrinsic(descriptor);
    }

    @Nullable
    private FunctionIntrinsic lookUpCache(@NotNull FunctionDescriptor descriptor) {
        return intrinsicCache.get(descriptor);
    }

    @NotNull
    private FunctionIntrinsic computeAndCacheIntrinsic(@NotNull FunctionDescriptor descriptor) {
        FunctionIntrinsic result = computeIntrinsic(descriptor);
        intrinsicCache.put(descriptor, result);
        return result;
    }

    @Nullable
    private static FunctionIntrinsic findIntrinsic(
            @NotNull FunctionDescriptor descriptor,
            @NotNull Collection<Pair<DescriptorPredicate, FunctionIntrinsic>> pairs
    ) {
        for (Pair<DescriptorPredicate, FunctionIntrinsic> pair : pairs) {
            if (pair.first.apply(descriptor)) {
                return pair.second;
            }
        }
        return null;
    }

    @NotNull
    private FunctionIntrinsic computeIntrinsic(@NotNull FunctionDescriptor descriptor) {
        Collection<Pair<DescriptorPredicate,FunctionIntrinsic>> pairs = intrinsics.get(descriptor.getName().getName());
        if (!pairs.isEmpty()) {
            FunctionIntrinsic intrinsic = findIntrinsic(descriptor, pairs);
            if (intrinsic != null) {
                return intrinsic;
            }
        }

        if (anyIntrinsics != null) {
            FunctionIntrinsic intrinsic = findIntrinsic(descriptor, anyIntrinsics);
            if (intrinsic != null) {
                return intrinsic;
            }
        }

        for (FunctionIntrinsicFactory factory : factories) {
            FunctionIntrinsic intrinsic = factory.getIntrinsic(descriptor);
            if (intrinsic != null) {
                return intrinsic;
            }
        }
        return FunctionIntrinsic.NO_INTRINSIC;
    }
}
