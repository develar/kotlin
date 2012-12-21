package org.jetbrains.jet.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModuleReference;

public class JsCompilerOutputPackagingElement extends JpsCompositeElementBase<JsCompilerOutputPackagingElement>
        implements JpsPackagingElement {
    private static final JpsElementChildRole<JpsModuleReference> MODULE_REFERENCE_CHILD_ROLE =
            JpsElementChildRoleBase.create("module reference");

    public JsCompilerOutputPackagingElement(JpsModuleReference moduleReference) {
        myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
    }

    private JsCompilerOutputPackagingElement(JsCompilerOutputPackagingElement original) {
        super(original);
    }

    @NotNull
    @Override
    public JsCompilerOutputPackagingElement createCopy() {
        return new JsCompilerOutputPackagingElement(this);
    }

    public JpsModuleReference getModuleReference() {
        return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
    }
}
