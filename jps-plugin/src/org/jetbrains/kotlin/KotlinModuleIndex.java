package org.jetbrains.kotlin;

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;

public class KotlinModuleIndex extends JpsCompositeElementBase<KotlinModuleIndex> {
    public static final JpsElementChildRole<KotlinModuleIndex> ROLE = JpsElementChildRoleBase.create("kotlin module index");

    private final MultiMap<JpsModule, JpsModule> parentToChildren = MultiMap.createSmartList();

    public static KotlinModuleIndex getIndex(@NotNull JpsProject project) {
        return project.getContainer().getChild(ROLE);
    }

    public static KotlinModuleIndex getOrCreateIndex(@NotNull JpsProject project) {
        KotlinModuleIndex index = getIndex(project);
        if (index == null) {
            index = project.getContainer().setChild(ROLE, new KotlinModuleIndex());
        }
        return index;
    }

    @NotNull
    @Override
    public KotlinModuleIndex createCopy() {
        return new KotlinModuleIndex();
    }

    public void addDependent(JpsModule child, JpsModule parent) {
        parentToChildren.putValue(parent, child);
    }

    public void addAllDependentsTo(JpsModule module, Collection<JpsModule> list) {
        for (JpsModule dependent : parentToChildren.get(module)) {
            list.add(dependent);
        }
    }
}