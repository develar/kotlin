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

package org.jetbrains.jet.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModuleReference;

public class JpsKotlinCompilerOutputPackagingElement extends JpsCompositeElementBase<JpsKotlinCompilerOutputPackagingElement>
        implements JpsPackagingElement {
    private static final JpsElementChildRole<JpsModuleReference> MODULE_REFERENCE_CHILD_ROLE =
            JpsElementChildRoleBase.create("module reference");

    public JpsKotlinCompilerOutputPackagingElement(JpsModuleReference moduleReference) {
        myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
    }

    private JpsKotlinCompilerOutputPackagingElement(JpsKotlinCompilerOutputPackagingElement original) {
        super(original);
    }

    @NotNull
    @Override
    public JpsKotlinCompilerOutputPackagingElement createCopy() {
        return new JpsKotlinCompilerOutputPackagingElement(this);
    }

    public JpsModuleReference getModuleReference() {
        return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
    }
}
