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
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;

import java.util.List;

public class DefinitionPlace {
    private int functionNameCounter;
    private int classNameCounter;
    private final List<JsNode> nodes;

    public DefinitionPlace(List<JsNode> nodes) {
        this.nodes = nodes;
    }

    public JsNameRef createReference(@NotNull JsFunction fun) {
        String name = "f$" + Integer.toString(functionNameCounter++, 36);
        JsNameRef nameRef = new JsNameRef(name);
        assert fun.getName() == null;
        fun.setName(name);
        nodes.add(fun);
        return nameRef;
    }

    public JsNameRef addInnerClass(@NotNull JsInvocation definition, @NotNull ClassDescriptor descriptor) {
        StringBuilder nameBuilder = new StringBuilder();
        if (!descriptor.getName().isSpecial()) {
            // we append member name to improve readability only, it is not required to prevent name clashing
            nameBuilder.append(descriptor.getName().asString()).append('_');
        }
        String name = nameBuilder.append('c').append('$').append(Integer.toString(classNameCounter++, 36)).toString();
        nodes.add(new JsVars(new JsVar(name, definition)));
        return new JsNameRef(name);
    }
}