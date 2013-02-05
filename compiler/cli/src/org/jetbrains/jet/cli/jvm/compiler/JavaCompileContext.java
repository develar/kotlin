package org.jetbrains.jet.cli.jvm.compiler;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.core.*;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.asJava.JavaElementFinder;
import org.jetbrains.jet.asJava.LightClassGenerationSupport;
import org.jetbrains.jet.lang.parsing.JetScriptDefinitionProvider;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.java.JetFilesProvider;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;
import org.jetbrains.kotlin.compiler.CompilerContextBase;

import java.util.List;

public class JavaCompileContext extends CompilerContextBase<JavaCoreProjectEnvironment> {
    private final CoreExternalAnnotationsManager annotationsManager;

    public JavaCompileContext(@NotNull Disposable parentDisposable, List<JetFile> sourceFiles) {
        super(new JavaCoreApplicationEnvironment(parentDisposable));

        registerFileTypes();

        projectEnvironment = new JavaCoreProjectEnvironment(parentDisposable, applicationEnvironment);
        projectEnvironment.getProject().registerService(JetScriptDefinitionProvider.class, new JetScriptDefinitionProvider());

        CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(), ClsCustomNavigationPolicy.EP_NAME,
                                                          ClsCustomNavigationPolicy.class);
        // ability to get text from annotations xml files
        applicationEnvironment.registerFileType(PlainTextFileType.INSTANCE, "xml");
        applicationEnvironment.registerParserDefinition(new JavaParserDefinition());

        MockProject project = projectEnvironment.getProject();
        project.registerService(JetFilesProvider.class, new CliJetFilesProvider(sourceFiles));
        project.registerService(CoreJavaFileManager.class, (CoreJavaFileManager) ServiceManager.getService(project, JavaFileManager.class));

        CliLightClassGenerationSupport cliLightClassGenerationSupport = new CliLightClassGenerationSupport();
        project.registerService(LightClassGenerationSupport.class, cliLightClassGenerationSupport);
        project.registerService(CliLightClassGenerationSupport.class, cliLightClassGenerationSupport);

        Extensions.getArea(project).getExtensionPoint(PsiElementFinder.EP_NAME).registerExtension(
                new JavaElementFinder(project, cliLightClassGenerationSupport));

        annotationsManager = new CoreExternalAnnotationsManager(project.getComponent(PsiManager.class));
        project.registerService(ExternalAnnotationsManager.class, annotationsManager);
    }

    public void initializeKotlinBuiltIns() {
        KotlinBuiltIns.initialize(getProject());
    }

    public CoreExternalAnnotationsManager getAnnotationsManager() {
        return annotationsManager;
    }

    public CoreApplicationEnvironment getApplicationEnvironment() {
        return applicationEnvironment;
    }

    public JavaCoreProjectEnvironment getProjectEnvironment() {
        return projectEnvironment;
    }
}
