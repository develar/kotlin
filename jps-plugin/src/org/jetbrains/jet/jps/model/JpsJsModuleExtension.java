package org.jetbrains.jet.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;

public class JpsJsModuleExtension extends JpsCompositeElementBase<JpsJsModuleExtension> {
  public static final JpsElementChildRole<JpsJsModuleExtension> ROLE = JpsElementChildRoleBase.create("KtToJs");

  private JpsJsModuleExtension(JpsJsModuleExtension original) {
    super(original);
  }

  @NotNull
  public JpsModule getModule() {
    return (JpsModule)myParent;
  }

  @NotNull
  @Override
  public JpsJsModuleExtension createCopy() {
    return new JpsJsModuleExtension(this);
  }
}