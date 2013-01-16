package org.jetbrains.jet.cli.jvm.compiler;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.asJava.JavaElementFinder;
import org.jetbrains.jet.asJava.LightClassGenerationSupport;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.java.JetFilesProvider;
import org.jetbrains.kotlin.compiler.CompileContext;

import java.util.List;

public class JavaCompileContext extends CompileContext {
    private final CoreExternalAnnotationsManager annotationsManager;

    public JavaCompileContext(@NotNull Disposable parentDisposable, List<JetFile> sourceFiles) {
        super(parentDisposable);

        applicationEnvironment.registerParserDefinition(new JavaParserDefinition());

        MockProject project = projectEnvironment.getProject();
        project.registerService(CoreJavaFileManager.class, (CoreJavaFileManager) ServiceManager.getService(project, JavaFileManager.class));

        CliLightClassGenerationSupport cliLightClassGenerationSupport = new CliLightClassGenerationSupport();
        project.registerService(LightClassGenerationSupport.class, cliLightClassGenerationSupport);
        project.registerService(CliLightClassGenerationSupport.class, cliLightClassGenerationSupport);
        Extensions.getArea(project).getExtensionPoint(PsiElementFinder.EP_NAME).registerExtension(
                new JavaElementFinder(project, cliLightClassGenerationSupport));
        project.registerService(JetFilesProvider.class, new CliJetFilesProvider(sourceFiles));

        annotationsManager = new CoreExternalAnnotationsManager(project.getComponent(PsiManager.class));
        project.registerService(ExternalAnnotationsManager.class, annotationsManager);
    }

    @Override
    protected void initializeKotlinBuiltIns() {
    }

    public void doInitializeKotlinBuiltIns() {
        super.initializeKotlinBuiltIns();
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
