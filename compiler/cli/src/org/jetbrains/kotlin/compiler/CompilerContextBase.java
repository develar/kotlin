package org.jetbrains.kotlin.compiler;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.parsing.JetParserDefinition;
import org.jetbrains.jet.plugin.JetFileType;

public class CompilerContextBase<P extends CoreProjectEnvironment> {
    protected final CoreApplicationEnvironment applicationEnvironment;
    protected P projectEnvironment;

    protected CompilerContextBase(@NotNull CoreApplicationEnvironment applicationEnvironment) {
        this.applicationEnvironment = applicationEnvironment;
    }

    protected void registerFileTypes() {
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, "kt");
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, "kts");
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, "ktm");
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, JetParserDefinition.KTSCRIPT_FILE_SUFFIX); // should be renamed to kts
        applicationEnvironment.registerFileType(JetFileType.INSTANCE, "jet");
        applicationEnvironment.registerParserDefinition(new JetParserDefinition());
    }

    public Project getProject() {
        return projectEnvironment.getProject();
    }

    public MockApplication getApplication() {
        return applicationEnvironment.getApplication();
    }

    public CoreLocalFileSystem getLocalFileSystem() {
        return applicationEnvironment.getLocalFileSystem();
    }

    public VirtualFileSystem getJarFileSystem() {
        return applicationEnvironment.getJarFileSystem();
    }
}
