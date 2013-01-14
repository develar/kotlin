package org.jetbrains.jet.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModuleReference;

public class JpsJsCompilerOutputPackagingElement extends JpsCompositeElementBase<JpsJsCompilerOutputPackagingElement>
        implements JpsPackagingElement {
    private static final JpsElementChildRole<JpsModuleReference> MODULE_REFERENCE_CHILD_ROLE =
            JpsElementChildRoleBase.create("module reference");

    public JpsJsCompilerOutputPackagingElement(JpsModuleReference moduleReference) {
        myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
        // todo we need Nik review
        JpsJsExtensionService.getInstance().setExtension(moduleReference);
    }

    private JpsJsCompilerOutputPackagingElement(JpsJsCompilerOutputPackagingElement original) {
        super(original);
    }

    @NotNull
    @Override
    public JpsJsCompilerOutputPackagingElement createCopy() {
        return new JpsJsCompilerOutputPackagingElement(this);
    }

    public JpsModuleReference getModuleReference() {
        return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
    }
}
