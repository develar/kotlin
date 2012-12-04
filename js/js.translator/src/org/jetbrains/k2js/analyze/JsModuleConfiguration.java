package org.jetbrains.k2js.analyze;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.DefaultModuleConfiguration;
import org.jetbrains.jet.lang.ModuleConfiguration;
import org.jetbrains.jet.lang.PlatformToKotlinClassMap;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
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
import java.util.List;

public class JsModuleConfiguration implements ModuleConfiguration {
    public static final Name STUBS_MODULE_NAME = Name.special("<stubs>");

    private final Project project;
    final BindingContext parentBindingContext;
    @Nullable
    private final ModuleConfiguration delegateConfiguration;

    private final ModuleDescriptor moduleDescriptor;

    @NotNull
    public static final List<ImportPath> DEFAULT_IMPORT_PATHS = Arrays.asList(
            new ImportPath("js.*"),
            new ImportPath("java.lang.*"),
            new ImportPath(KotlinBuiltIns.getInstance().getBuiltInsPackageFqName(), true),
            new ImportPath("kotlin.*"));

    BindingContext bindingContext;

    public JsModuleConfiguration(Project project) {
        this(new ModuleDescriptor(Name.special("<module>")), project, null);
    }

    public JsModuleConfiguration(ModuleDescriptor moduleDescriptor, Project project) {
        this(moduleDescriptor, project, null);
    }

    public JsModuleConfiguration(ModuleDescriptor moduleDescriptor, Project project, @Nullable JsModuleConfiguration parentJsModuleConfiguration) {
        this.moduleDescriptor = moduleDescriptor;
        this.project = project;
        if (parentJsModuleConfiguration == null) {
            parentBindingContext = null;
            delegateConfiguration = DefaultModuleConfiguration.createStandardConfiguration(project);
        }
        else {
            parentBindingContext = parentJsModuleConfiguration.bindingContext;
            delegateConfiguration = null;
        }
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
        if (parentBindingContext != null) {
            FqName qualifiedName = namespaceDescriptor.getQualifiedName();
            NamespaceDescriptor alreadyAnalyzedNamespace =
                    parentBindingContext.get(BindingContext.FQNAME_TO_NAMESPACE_DESCRIPTOR, qualifiedName);
            if (alreadyAnalyzedNamespace != null) {
                namespaceMemberScope.importScope(alreadyAnalyzedNamespace.getMemberScope());
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
}
