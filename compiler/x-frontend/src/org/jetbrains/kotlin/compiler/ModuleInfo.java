package org.jetbrains.kotlin.compiler;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.DefaultModuleConfiguration;
import org.jetbrains.jet.lang.ModuleConfiguration;
import org.jetbrains.jet.lang.PlatformToKotlinClassMap;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.psi.JetImportDirective;
import org.jetbrains.jet.lang.psi.JetPsiFactory;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingTrace;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.ImportPath;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.resolve.scopes.WritableScope;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ModuleInfo implements ModuleConfiguration {
    public static final Name STUBS_MODULE_NAME = Name.special('<' + "stubs" + '>');

    private final Project project;

    public final List<ModuleInfo> dependencies;
    @Nullable
    private final ModuleConfiguration delegateConfiguration;

    private final ModuleDescriptor moduleDescriptor;

    public BindingContext bindingContext;

    private String normalName;

    // todo use dirty files holder and remove this field
    List<JetFile> sourceFiles;

    private final Set<ModuleInfo> providedDependencies;

    @NotNull
    private static final List<ImportPath> DEFAULT_IMPORT_PATHS = Arrays.asList(
            new ImportPath("js.*"),
            new ImportPath("java.lang.*"),
            new ImportPath(KotlinBuiltIns.getInstance().getBuiltInsPackageFqName(), true),
            new ImportPath("kotlin.*"));

    public ModuleInfo(Project project) {
        this(new ModuleDescriptor(Name.special("<module>")), project, null, null);
    }

    public ModuleInfo(ModuleDescriptor moduleDescriptor, Project project) {
        this(moduleDescriptor, project, null, null);
    }

    public ModuleInfo(ModuleDescriptor moduleDescriptor, Project project, @Nullable ModuleInfo dependency) {
        this(moduleDescriptor, project, Collections.singletonList(dependency), null);
    }

    public ModuleInfo(String name, Project project, @Nullable List<ModuleInfo> dependencies) {
        this(name, project, dependencies, null);
    }

    public ModuleInfo(String name, Project project, @Nullable List<ModuleInfo> dependencies, @Nullable Set<ModuleInfo> providedDependencies) {
        this(new ModuleDescriptor(Name.special('<' + name + '>')), project, dependencies, providedDependencies);
        normalName = name;
    }

    public ModuleInfo(ModuleDescriptor moduleDescriptor, Project project, @Nullable List<ModuleInfo> dependencies, @Nullable Set<ModuleInfo> providedDependencies) {
        this.moduleDescriptor = moduleDescriptor;
        this.project = project;
        this.providedDependencies = providedDependencies == null ? Collections.<ModuleInfo>emptySet() : providedDependencies;
        if (dependencies == null || dependencies.isEmpty()) {
            this.dependencies = null;
            delegateConfiguration = DefaultModuleConfiguration.createStandardConfiguration(project);
        }
        else {
            this.dependencies = dependencies;
            delegateConfiguration = null;
        }
    }

    public boolean isDependencyProvided(ModuleInfo dependency) {
        return providedDependencies.contains(dependency);
    }

    public boolean isDependencyProvided(ModuleDescriptor descriptor) {
        ModuleInfo dependency = findDependency(descriptor);
        return dependency != null && providedDependencies.contains(dependency);
    }

    @NotNull
    public List<ModuleInfo> getDependencies() {
        return dependencies == null ? Collections.<ModuleInfo>emptyList() : dependencies;
    }

    public String getName() {
        if (normalName == null) {
            String specialName = moduleDescriptor.getName().getName();
            normalName = specialName.substring(1, specialName.length() - 1);
        }
        return normalName;
    }

    public BindingContext getBindingContext() {
        return bindingContext;
    }

    public Project getProject() {
        return project;
    }

    public ModuleDescriptor getModuleDescriptor() {
        return moduleDescriptor;
    }

    @Override
    public void addDefaultImports(@NotNull Collection<JetImportDirective> directives) {
        for (ImportPath path : DEFAULT_IMPORT_PATHS) {
            directives.add(JetPsiFactory.createImportDirective(project, path));
        }
        if (delegateConfiguration != null) {
            delegateConfiguration.addDefaultImports(directives);
        }
    }

    @Override
    public void extendNamespaceScope(
            @NotNull BindingTrace trace, @NotNull NamespaceDescriptor namespaceDescriptor, @NotNull WritableScope namespaceMemberScope
    ) {
        if (dependencies != null) {
            FqName qualifiedName = namespaceDescriptor.getQualifiedName();
            for (ModuleInfo dependency : dependencies) {
                NamespaceDescriptor namespaceDependency = dependency.bindingContext.get(BindingContext.FQNAME_TO_NAMESPACE_DESCRIPTOR,
                                                                                        qualifiedName);
                if (namespaceDependency != null) {
                    namespaceMemberScope.importScope(namespaceDependency.getMemberScope());
                }
            }
        }
        else if (DescriptorUtils.isRootNamespace(namespaceDescriptor)) {
            namespaceMemberScope.importScope(KotlinBuiltIns.getInstance().getBuiltInsScope());
        }
        else if (delegateConfiguration != null) {
            delegateConfiguration.extendNamespaceScope(trace, namespaceDescriptor, namespaceMemberScope);
        }
    }

    @NotNull
    @Override
    public PlatformToKotlinClassMap getPlatformToKotlinClassMap() {
        return PlatformToKotlinClassMap.EMPTY;
    }

    @Nullable
    public ModuleInfo findDependency(ModuleDescriptor descriptor) {
        for (ModuleInfo dependency : dependencies) {
            if (dependency.moduleDescriptor == descriptor) {
                return dependency;
            }
        }
        return null;
    }

    @Nullable
    public BindingContext findBindingContext(@NotNull ModuleDescriptor moduleDescriptor) {
        if (this.moduleDescriptor == moduleDescriptor) {
            return bindingContext;
        }
        ModuleInfo dependency = findDependency(moduleDescriptor);
        return dependency == null ? null : dependency.getBindingContext();
    }
}
