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

package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.initializer.InitializerUtils;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.Arrays;
import java.util.Collection;

import static com.google.dart.compiler.backend.js.ast.JsVars.JsVar;
import static org.jetbrains.jet.lang.resolve.DescriptorUtils.getClassDescriptorForType;
import static org.jetbrains.jet.lang.resolve.DescriptorUtils.isNotAny;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getClassDescriptor;

public final class ClassDeclarationTranslator {
    private ClassDeclarationTranslator() {
    }

    @Nullable
    public static JsPropertyInitializer translate(@NotNull JetClassOrObject declaration, TranslationContext context) {
        ClassDescriptor descriptor = getClassDescriptor(context.bindingContext(), declaration);
        if (context.predefinedAnnotationManager().hasOptionsArg(descriptor)) {
            return null;
        }

        JsExpression value = new ClassTranslator(declaration, descriptor, context).translate(context);
        JsExpression valueGetter;
        if (context.isEcma5()) {
            if (hasSupertypes(descriptor, context)) {
                JsVar resultVar = new JsVar("r", value);
                JsNameRef resultVarRef = new JsNameRef(resultVar.getName());
                JsBlock body = new JsBlock(new JsVars(resultVar),
                                           JsAstUtils.defineProperty(descriptor.getName().asString(), resultVarRef).asStatement(),
                                           new JsReturn(resultVarRef));
                valueGetter = new JsObjectLiteral(Arrays.asList(new JsPropertyInitializer("get", new JsFunction(context.scope(), body))));
            }
            else {
                valueGetter = InitializerUtils.toDataDescriptor(value, context);
            }
        }
        else {
            JsNameRef cacheVarRef = new JsNameRef(descriptor.getName().asString() + "$c", JsLiteral.THIS);
            JsVar resultVar = new JsVar("r", cacheVarRef);
            JsNameRef resultVarRef = new JsNameRef(resultVar.getName());
            JsIf ifNotCreated = new JsIf(JsAstUtils.equality(resultVarRef, JsLiteral.UNDEFINED),
                                         JsAstUtils.assignment(cacheVarRef, JsAstUtils.assignment(resultVarRef, value)).asStatement());
            JsBlock body = new JsBlock(new JsVars(resultVar), ifNotCreated, new JsReturn(resultVarRef));
            valueGetter = new JsFunction(context.scope(), body);
        }
        return new JsPropertyInitializer(descriptor.getName().asString(), valueGetter);
    }

    private static boolean hasSupertypes(ClassDescriptor descriptor, TranslationContext context) {
        Collection<JetType> supertypes = descriptor.getTypeConstructor().getSupertypes();
        if (!supertypes.isEmpty()) {
            for (JetType type : supertypes) {
                ClassDescriptor result = getClassDescriptorForType(type);
                if (isNotAny(result) && !context.isNative(result)) {
                    return true;
                }
            }
        }
        return false;
    }
}